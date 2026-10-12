package app.lawnchair.assistant

import android.app.ActivityManager
import android.content.Context
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Chatterbox's local ONNX speech pipeline.
 *
 * The model uses substantial memory. Load it only when speech is requested, and fall back
 * to Android TextToSpeech if this device cannot load the downloaded ONNX files.
 */
object ChatterboxOnnxEngine {
    private const val SAMPLE_RATE = 24_000
    private const val START_SPEECH_TOKEN = 6561L
    private const val STOP_SPEECH_TOKEN = 6562L
    private const val EXAGGERATION_TOKEN = 6563L
    private const val START_TEXT_TOKEN = 255L
    private const val STOP_TEXT_TOKEN = 0L
    private const val EXAGGERATION = 0.5f
    private const val MAX_NEW_TOKENS = 256
    private const val NUM_KV_HEADS = 16
    private const val HEAD_DIM = 64

    private data class FloatData(val shape: LongArray, val values: FloatArray)
    private data class LongData(val shape: LongArray, val values: LongArray)

    @Volatile private var environment: OrtEnvironment? = null
    @Volatile private var speechEncoder: OrtSession? = null
    @Volatile private var embedTokens: OrtSession? = null
    @Volatile private var languageModel: OrtSession? = null
    @Volatile private var conditionalDecoder: OrtSession? = null
    @Volatile private var tokenizer: ChatterboxTokenizer? = null

    val isLoaded: Boolean
        get() = speechEncoder != null && embedTokens != null &&
            languageModel != null && conditionalDecoder != null

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        if (AssistantModelStore.voiceState(context) != AssistantModelStore.AssetState.DOWNLOADED) {
            throw IllegalStateException("Download the Chatterbox voice first.")
        }
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        if (memory.availMem < 1_500_000_000L) {
            throw IllegalStateException("Chatterbox needs more free memory. Close some apps and try again.")
        }

        val root = AssistantModelStore.voiceDirectory(context)
        val onnx = File(root, "onnx")
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(
                (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
            )
            speechEncoder = env.createSession(File(onnx, "speech_encoder.onnx").absolutePath, options)
            embedTokens = env.createSession(File(onnx, "embed_tokens.onnx").absolutePath, options)
            languageModel = env.createSession(File(onnx, "language_model_q4.onnx").absolutePath, options)
            conditionalDecoder = env.createSession(File(onnx, "conditional_decoder.onnx").absolutePath, options)
            tokenizer = ChatterboxTokenizer(File(root, "tokenizer.json"))
            environment = env
        } catch (error: Throwable) {
            releaseSessions()
            throw IllegalStateException(
                "Chatterbox could not be loaded on this device. The phone voice will be used instead.",
                error,
            )
        } finally {
            options.close()
        }
    }

    @Synchronized
    private fun releaseSessions() {
        runCatching { conditionalDecoder?.close() }
        runCatching { languageModel?.close() }
        runCatching { embedTokens?.close() }
        runCatching { speechEncoder?.close() }
        conditionalDecoder = null
        languageModel = null
        embedTokens = null
        speechEncoder = null
        tokenizer = null
    }

    /**
     * Synthesize English text to a temporary PCM WAV file. Call from a background thread.
     * This implementation uses the model's default reference voice.
     */
    fun synthesizeToWav(context: Context, text: String): File {
        require(text.isNotBlank()) { "There is no text to read aloud." }
        ensureLoaded(context)

        val env = environment ?: error("The voice engine is not ready.")
        val encoder = speechEncoder ?: error("The speech encoder is not ready.")
        val embedding = embedTokens ?: error("The text encoder is not ready.")
        val llm = languageModel ?: error("The voice model is not ready.")
        val decoder = conditionalDecoder ?: error("The audio decoder is not ready.")
        val textTokenizer = tokenizer ?: error("The text tokenizer is not ready.")

        val voiceAudio = readWaveMono(File(AssistantModelStore.voiceDirectory(context), "default_voice.wav"))
        val audioTensor = floatTensor(env, voiceAudio, longArrayOf(1, voiceAudio.size.toLong()))
        val speechConditioning = encoder.run(mapOf("audio_values" to audioTensor))
        audioTensor.close()

        try {
            val condEmbedding = tensorFloats(speechConditioning.get(0) as OnnxTensor)
            val promptTokens = tensorLongs(speechConditioning.get(1) as OnnxTensor).values
            val speakerEmbedding = speechConditioning.get(2) as OnnxTensor
            val speakerFeatures = speechConditioning.get(3) as OnnxTensor

            val ids = textTokenizer.encode(text).let { body ->
                longArrayOf(EXAGGERATION_TOKEN, START_TEXT_TOKEN) + body +
                    longArrayOf(STOP_TEXT_TOKEN, START_SPEECH_TOKEN, START_SPEECH_TOKEN)
            }
            val positions = LongArray(ids.size) { index ->
                if (ids[index] >= START_SPEECH_TOKEN) 0L else index.toLong() - 1L
            }
            val textEmbeddings = runTextEmbedding(env, embedding, ids, positions)
            val combined = concatenateSequence(condEmbedding, textEmbeddings)
            var nextEmbeddings = floatTensor(env, combined.values, combined.shape)
            var attention = LongArray(combined.shape[1].toInt()) { 1L }

            val inputNames = llm.inputInfo.keys.toList()
            val cacheInputNames = inputNames
                .filter { it.startsWith("past_key_values.") }
                .sortedWith(compareBy({ cacheLayer(it) }, { cacheTensorOrder(it) }))
            val cacheOutputIndices = llm.outputInfo.keys.withIndex()
                .filter { it.value.contains("key") || it.value.contains("value") }
                .sortedWith(compareBy({ cacheLayer(it.value) }, { cacheTensorOrder(it.value) }))
                .map { it.index }
            val logitsIndex = llm.outputInfo.keys.indexOfFirst { it.contains("logits") }
                .takeIf { it >= 0 } ?: 0
            if (cacheInputNames.isEmpty() || cacheOutputIndices.size != cacheInputNames.size) {
                nextEmbeddings.close()
                throw IllegalStateException("This Chatterbox model has an unsupported memory layout.")
            }

            val emptyCache = cacheInputNames.map {
                floatTensor(env, FloatArray(0), longArrayOf(1, NUM_KV_HEADS.toLong(), 0, HEAD_DIM.toLong()))
            }
            var cacheOwner: OrtSession.Result? = null
            var cacheTensors: List<OnnxTensor> = emptyCache
            val generated = mutableListOf<Long>()
            var sequenceLength = combined.shape[1].toInt()

            try {
                for (step in 0 until MAX_NEW_TOKENS) {
                    val attentionTensor = longTensor(
                        env, attention, longArrayOf(1, attention.size.toLong())
                    )
                    val inputs = linkedMapOf<String, OnnxTensor>(
                        "inputs_embeds" to nextEmbeddings,
                        "attention_mask" to attentionTensor,
                    )
                    if (inputNames.contains("position_ids")) {
                        val positionIds = if (step == 0) {
                            LongArray(sequenceLength) { it.toLong() }
                        } else {
                            longArrayOf(sequenceLength.toLong())
                        }
                        inputs["position_ids"] = longTensor(
                            env, positionIds, longArrayOf(1, positionIds.size.toLong())
                        )
                    }
                    cacheInputNames.forEachIndexed { index, name ->
                        inputs[name] = cacheTensors[index]
                    }

                    val result = llm.run(inputs)
                    nextEmbeddings.close()
                    attentionTensor.close()
                    if (inputNames.contains("position_ids")) {
                        inputs["position_ids"]?.close()
                    }
                    if (cacheOwner == null) emptyCache.forEach { runCatching { it.close() } }
                    cacheOwner?.close()
                    cacheOwner = result
                    cacheTensors = cacheOutputIndices.map { result.get(it) as OnnxTensor }

                    val logits = result.get(logitsIndex) as OnnxTensor
                    val scoresBuffer = logits.floatBuffer.duplicate()
                    val allScores = FloatArray(scoresBuffer.remaining())
                    scoresBuffer.get(allScores)
                    val vocabSize = logits.info.shape.last().toInt()
                    if (vocabSize <= 0 || allScores.size < vocabSize) {
                        throw IllegalStateException("Chatterbox returned invalid speech tokens.")
                    }
                    val scores = allScores.copyOfRange(allScores.size - vocabSize, allScores.size)
                    generated.forEach { token ->
                        val index = token.toInt()
                        if (index in scores.indices) {
                            scores[index] = if (scores[index] < 0f) scores[index] * 1.2f else scores[index] / 1.2f
                        }
                    }
                    val nextToken = scores.indices.maxByOrNull { scores[it] }?.toLong()
                        ?: throw IllegalStateException("Chatterbox returned no speech tokens.")
                    if (nextToken == STOP_SPEECH_TOKEN) break
                    generated += nextToken
                    if (step == MAX_NEW_TOKENS - 1) break

                    val oneTokenEmbedding = runTextEmbedding(
                        env,
                        embedding,
                        longArrayOf(nextToken),
                        longArrayOf(sequenceLength.toLong()),
                    )
                    nextEmbeddings = floatTensor(env, oneTokenEmbedding.values, oneTokenEmbedding.shape)
                    attention = attention + 1L
                    sequenceLength += 1
                }
            } finally {
                runCatching { nextEmbeddings.close() }
                emptyCache.forEach { runCatching { it.close() } }
                runCatching { cacheOwner?.close() }
            }

            if (generated.isEmpty()) {
                throw IllegalStateException("Chatterbox did not generate speech.")
            }
            val speechTokens = promptTokens + generated.toLongArray()
            val speechTokenTensor = longTensor(
                env, speechTokens, longArrayOf(1, speechTokens.size.toLong())
            )
            val audioResult = decoder.run(
                mapOf(
                    "speech_tokens" to speechTokenTensor,
                    "speaker_embeddings" to speakerEmbedding,
                    "speaker_features" to speakerFeatures,
                )
            )
            speechTokenTensor.close()
            try {
                val waveform = tensorFloats(audioResult.get(0) as OnnxTensor).values
                if (waveform.size < SAMPLE_RATE / 10) {
                    throw IllegalStateException("Chatterbox produced an audio clip that was too short.")
                }
                return writeWaveFile(context, waveform)
            } finally {
                audioResult.close()
            }
        } finally {
            speechConditioning.close()
        }
    }

    private fun runTextEmbedding(
        env: OrtEnvironment,
        session: OrtSession,
        inputIds: LongArray,
        positionIds: LongArray,
    ): FloatData {
        val idsTensor = longTensor(env, inputIds, longArrayOf(1, inputIds.size.toLong()))
        val positionTensor = longTensor(env, positionIds, longArrayOf(1, positionIds.size.toLong()))
        val exaggerationTensor = floatTensor(env, floatArrayOf(EXAGGERATION), longArrayOf(1))
        try {
            val inputs = linkedMapOf<String, OnnxTensor>()
            if (session.inputInfo.containsKey("input_ids")) inputs["input_ids"] = idsTensor
            if (session.inputInfo.containsKey("position_ids")) inputs["position_ids"] = positionTensor
            if (session.inputInfo.containsKey("exaggeration")) inputs["exaggeration"] = exaggerationTensor
            val result = session.run(inputs)
            try {
                return tensorFloats(result.get(0) as OnnxTensor)
            } finally {
                result.close()
            }
        } finally {
            idsTensor.close()
            positionTensor.close()
            exaggerationTensor.close()
        }
    }

    private fun concatenateSequence(left: FloatData, right: FloatData): FloatData {
        require(left.shape.size == 3 && right.shape.size == 3) {
            "Chatterbox returned an unexpected text embedding shape."
        }
        val hidden = left.shape[2]
        require(left.shape[0] == 1L && right.shape[0] == 1L && right.shape[2] == hidden) {
            "Chatterbox returned incompatible text embeddings."
        }
        return FloatData(
            longArrayOf(1L, left.shape[1] + right.shape[1], hidden),
            left.values + right.values,
        )
    }

    private fun tensorFloats(tensor: OnnxTensor): FloatData {
        val buffer = tensor.floatBuffer.duplicate()
        val values = FloatArray(buffer.remaining())
        buffer.get(values)
        return FloatData(tensor.info.shape, values)
    }

    private fun tensorLongs(tensor: OnnxTensor): LongData {
        val buffer = tensor.longBuffer.duplicate()
        val values = LongArray(buffer.remaining())
        buffer.get(values)
        return LongData(tensor.info.shape, values)
    }

    private fun floatTensor(env: OrtEnvironment, values: FloatArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)

    private fun longTensor(env: OrtEnvironment, values: LongArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)

    private fun cacheLayer(name: String): Int =
        Regex("""(?:past_key_values|present)\.(\d+)\.""")
            .find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private fun cacheTensorOrder(name: String): Int = if (name.endsWith(".key")) 0 else 1

    private fun readWaveMono(file: File): FloatArray {
        require(file.isFile && file.length() > 44L) { "The Chatterbox reference voice is missing." }
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var audioFormat = 1
        var channels = 1
        var sampleRate = SAMPLE_RATE
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLength = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunk = String(bytes, offset, 4, Charsets.US_ASCII)
            val length = buffer.getInt(offset + 4).coerceAtLeast(0)
            val start = offset + 8
            if (start + length > bytes.size) break
            when (chunk) {
                "fmt " -> if (length >= 16) {
                    audioFormat = buffer.getShort(start).toInt() and 0xffff
                    channels = buffer.getShort(start + 2).toInt() and 0xffff
                    sampleRate = buffer.getInt(start + 4)
                    bitsPerSample = buffer.getShort(start + 14).toInt() and 0xffff
                }
                "data" -> {
                    dataOffset = start
                    dataLength = length
                }
            }
            offset = start + length + (length and 1)
        }
        if (dataOffset < 0 || channels < 1 || sampleRate < 1) {
            throw IllegalStateException("The Chatterbox reference voice is not a valid WAV file.")
        }
        val bytesPerSample = bitsPerSample / 8
        require(bytesPerSample in 1..4 && audioFormat in listOf(1, 3)) {
            "This reference voice audio format is not supported."
        }
        val frameSize = bytesPerSample * channels
        val frames = dataLength / frameSize
        val mono = FloatArray(frames)
        var cursor = dataOffset
        for (frame in 0 until frames) {
            var sum = 0f
            repeat(channels) {
                val value = when {
                    audioFormat == 3 && bitsPerSample == 32 -> buffer.getFloat(cursor)
                    audioFormat == 1 && bitsPerSample == 8 -> ((bytes[cursor].toInt() and 0xff) - 128) / 128f
                    audioFormat == 1 && bitsPerSample == 16 -> {
                        val raw = (bytes[cursor].toInt() and 0xff) or (bytes[cursor + 1].toInt() shl 8)
                        raw.toShort().toFloat() / 32768f
                    }
                    audioFormat == 1 && bitsPerSample == 24 -> {
                        var raw = (bytes[cursor].toInt() and 0xff) or
                            ((bytes[cursor + 1].toInt() and 0xff) shl 8) or
                            ((bytes[cursor + 2].toInt() and 0xff) shl 16)
                        if (raw and 0x800000 != 0) raw = raw or -0x1000000
                        raw.toFloat() / 8_388_608f
                    }
                    audioFormat == 1 && bitsPerSample == 32 -> buffer.getInt(cursor).toFloat() / 2_147_483_648f
                    else -> throw IllegalStateException("This reference voice bit depth is not supported.")
                }
                sum += value
                cursor += bytesPerSample
            }
            mono[frame] = (sum / channels).coerceIn(-1f, 1f)
        }
        return resample(mono, sampleRate, SAMPLE_RATE)
    }

    private fun resample(input: FloatArray, originalRate: Int, targetRate: Int): FloatArray {
        if (originalRate == targetRate || input.isEmpty()) return input
        val outputCount = (input.size.toDouble() * targetRate / originalRate).roundToInt().coerceAtLeast(1)
        return FloatArray(outputCount) { index ->
            val position = index.toDouble() * originalRate / targetRate
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = min(left + 1, input.lastIndex)
            val fraction = (position - left).toFloat()
            input[left] * (1f - fraction) + input[right] * fraction
        }
    }

    private fun writeWaveFile(context: Context, samples: FloatArray): File {
        val output = File(context.cacheDir, "nexus-chatterbox-" + System.currentTimeMillis() + ".wav")
        val dataSize = samples.size * 2
        BufferedOutputStream(FileOutputStream(output)).use { stream ->
            stream.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLE(stream, 36 + dataSize)
            stream.write("WAVE".toByteArray(Charsets.US_ASCII))
            stream.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLE(stream, 16)
            writeShortLE(stream, 1)
            writeShortLE(stream, 1)
            writeIntLE(stream, SAMPLE_RATE)
            writeIntLE(stream, SAMPLE_RATE * 2)
            writeShortLE(stream, 2)
            writeShortLE(stream, 16)
            stream.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLE(stream, dataSize)
            samples.forEach { sample ->
                writeShortLE(stream, (sample.coerceIn(-1f, 1f) * 32767f).roundToInt())
            }
        }
        return output
    }

    private fun writeIntLE(stream: BufferedOutputStream, value: Int) {
        stream.write(value and 0xff)
        stream.write((value ushr 8) and 0xff)
        stream.write((value ushr 16) and 0xff)
        stream.write((value ushr 24) and 0xff)
    }

    private fun writeShortLE(stream: BufferedOutputStream, value: Int) {
        stream.write(value and 0xff)
        stream.write((value ushr 8) and 0xff)
    }

    /**
     * Hugging Face's Chatterbox tokenizer uses GPT-style byte-level BPE. Implement the byte
     * encoder locally so no separate tokenizer package or network call is needed.
     */
    private class ChatterboxTokenizer(file: File) {
        private val vocab = HashMap<String, Long>()
        private val mergeRanks = HashMap<Pair<String, String>, Int>()
        private val byteEncoder = HashMap<Int, Char>()
        private val pattern = Regex("(?i:'s|'t|'re|'ve|'m|'ll|'d)| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+")

        init {
            val root = JSONObject(file.readText())
            val model = root.getJSONObject("model")
            val vocabJson = model.getJSONObject("vocab")
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                vocab[token] = vocabJson.getLong(token)
            }
            val merges = model.getJSONArray("merges")
            for (index in 0 until merges.length()) {
                val entry = merges.get(index)
                val pair = when (entry) {
                    is JSONArray -> if (entry.length() >= 2) {
                        entry.getString(0) to entry.getString(1)
                    } else null
                    is String -> {
                        val divider = entry.indexOf(' ')
                        if (divider >= 0) entry.substring(0, divider) to entry.substring(divider + 1) else null
                    }
                    else -> null
                }
                if (pair != null) mergeRanks[pair] = index
            }

            val visible = mutableListOf<Int>()
            visible.addAll(33..126)
            visible.addAll(161..172)
            visible.addAll(174..255)
            var extra = 0
            for (byte in 0..255) {
                if (!visible.contains(byte)) {
                    visible.add(byte)
                    byteEncoder[byte] = (256 + extra).toChar()
                    extra++
                }
            }
            visible.filter { it !in byteEncoder }.forEach { byteEncoder[it] = it.toChar() }
        }

        fun encode(text: String): LongArray {
            val result = ArrayList<Long>()
            val matcher = pattern.toPattern().matcher(text.replace(Regex("\\s+"), " "))
            while (matcher.find()) {
                val bytes = matcher.group().toByteArray(Charsets.UTF_8)
                var symbols = bytes.map { byteEncoder[it.toInt() and 0xff].toString() }
                symbols = applyBpe(symbols)
                symbols.forEach { result += (vocab[it] ?: 1L) }
            }
            if (result.isEmpty()) return longArrayOf(1L)
            return result.toLongArray()
        }

        private fun applyBpe(initial: List<String>): List<String> {
            val symbols = initial.toMutableList()
            while (symbols.size > 1) {
                var bestRank = Int.MAX_VALUE
                var bestIndex = -1
                for (index in 0 until symbols.lastIndex) {
                    val rank = mergeRanks[symbols[index] to symbols[index + 1]] ?: continue
                    if (rank < bestRank) {
                        bestRank = rank
                        bestIndex = index
                    }
                }
                if (bestIndex < 0) break
                symbols[bestIndex] = symbols[bestIndex] + symbols[bestIndex + 1]
                symbols.removeAt(bestIndex + 1)
            }
            return symbols
        }
    }
}
