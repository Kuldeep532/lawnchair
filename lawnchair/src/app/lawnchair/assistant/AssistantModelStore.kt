package app.lawnchair.assistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads model files into app-private storage. Download completion and inference readiness
 * are intentionally separate states; a runtime must successfully load a model before using it.
 */
object AssistantModelStore {
    const val LOCAL_MODEL_REPO = "bartowski/SmolLM2-135M-Instruct-GGUF"
    const val CHATTERBOX_REPO = "onnx-community/chatterbox-ONNX"

    const val LOCAL_MODEL_FILENAME = "SmolLM2-135M-Instruct-Q4_K_M.gguf"

    private const val LOCAL_MODEL_URL =
        "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"

    // Q4 Chatterbox ONNX variant avoids downloading every precision variant from the repository.
    // These files total roughly 1.5 GB. Chatterbox is an English ONNX export; Hindi output remains
    // on Android TTS unless a compatible Hindi neural model/runtime is separately integrated.
    private val chatterboxFiles = listOf(
        "config.json",
        "default_voice.wav",
        "generation_config.json",
        "preprocessor_config.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "onnx/conditional_decoder.onnx",
        "onnx/conditional_decoder.onnx_data",
        "onnx/embed_tokens.onnx",
        "onnx/embed_tokens.onnx_data",
        "onnx/language_model_q4.onnx",
        "onnx/language_model_q4.onnx_data",
        "onnx/speech_encoder.onnx",
        "onnx/speech_encoder.onnx_data",
    )

    enum class AssetState { MISSING, PARTIAL, DOWNLOADED }

    data class DownloadProgress(
        val label: String,
        val percent: Int,
        val message: String,
    )

    private val localBusy = AtomicBoolean(false)
    private val voiceBusy = AtomicBoolean(false)

    fun localDirectory(context: Context): File =
        File(context.filesDir, "assistant-models/smollm2-135m").apply { mkdirs() }

    fun localModelFile(context: Context): File =
        File(localDirectory(context), LOCAL_MODEL_FILENAME)

    fun voiceDirectory(context: Context): File =
        File(context.filesDir, "assistant-models/chatterbox-onnx").apply { mkdirs() }

    fun localState(context: Context): AssetState {
        val dir = localDirectory(context)
        val model = localModelFile(context)
        val complete = File(dir, ".download_complete")
        return when {
            complete.isFile && model.isFile && model.length() >= 50_000_000L -> AssetState.DOWNLOADED
            dir.listFiles()?.any { it.isFile && it.length() > 0L } == true -> AssetState.PARTIAL
            else -> AssetState.MISSING
        }
    }

    fun voiceState(context: Context): AssetState {
        val dir = voiceDirectory(context)
        val complete = File(dir, ".download_complete")
        val allFilesPresent = chatterboxFiles.all { relative ->
            val file = File(dir, relative)
            file.isFile && file.length() > 0L
        }
        return when {
            complete.isFile && allFilesPresent -> AssetState.DOWNLOADED
            dir.walkTopDown().any { it.isFile && it.length() > 0L } -> AssetState.PARTIAL
            else -> AssetState.MISSING
        }
    }

    fun downloadRequired(context: Context, onProgress: (DownloadProgress) -> Unit) {
        if (localState(context) != AssetState.DOWNLOADED) {
            downloadLocalModel(context, onProgress)
        }
        if (voiceState(context) != AssetState.DOWNLOADED) {
            downloadChatterboxVoice(context, onProgress)
        }
    }

    fun downloadLocalModel(context: Context, onProgress: (DownloadProgress) -> Unit) {
        check(localBusy.compareAndSet(false, true)) { "The local model download is already in progress." }
        try {
            val dir = localDirectory(context)
            val model = localModelFile(context)
            val partial = File(dir, LOCAL_MODEL_FILENAME + ".part")
            if (model.isFile && model.length() >= 50_000_000L) {
                File(dir, ".download_complete").writeText("downloaded")
                onProgress(DownloadProgress("Local assistant model", 100, "Local model has already been downloaded."))
                return
            }
            File(dir, ".download_complete").delete()
            downloadFile(LOCAL_MODEL_URL, partial, "Local assistant model", onProgress)
            if (!partial.renameTo(model)) {
                partial.copyTo(model, overwrite = true)
                partial.delete()
            }
            if (model.length() < 50_000_000L) {
                model.delete()
                throw IllegalStateException("The local model download was incomplete. Please try again.")
            }
            File(dir, ".download_complete").writeText("downloaded")
            onProgress(DownloadProgress("Local assistant model", 100, "Local model downloaded."))
        } finally {
            localBusy.set(false)
        }
    }

    fun downloadChatterboxVoice(context: Context, onProgress: (DownloadProgress) -> Unit) {
        check(voiceBusy.compareAndSet(false, true)) { "The voice download is already in progress." }
        try {
            val dir = voiceDirectory(context)
            if (dir.usableSpace < 1_800_000_000L) {
                throw IllegalStateException("Please free at least 2 GB of storage, then try downloading Chatterbox again.")
            }
            val base = "https://huggingface.co/$CHATTERBOX_REPO/resolve/main/"
            var completed = 0
            val filesToDownload = chatterboxFiles
            File(dir, ".download_complete").delete()
            for (relative in filesToDownload) {
                val target = File(dir, relative)
                if (target.isFile && target.length() > 0L) {
                    completed++
                    continue
                }
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, target.name + ".part")
                downloadFile(base + relative, partial, "Chatterbox voice") { update ->
                    val overall = (((completed * 100) + update.percent) / filesToDownload.size).coerceIn(0, 100)
                    onProgress(DownloadProgress("Chatterbox voice", overall, "Downloading " + target.name))
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                completed++
                onProgress(
                    DownloadProgress(
                        "Chatterbox voice",
                        completed * 100 / filesToDownload.size,
                        "Downloaded " + target.name,
                    )
                )
            }
            if (!filesToDownload.all { relative ->
                    val file = File(dir, relative)
                    file.isFile && file.length() > 0L
                }
            ) {
                throw IllegalStateException("Some Chatterbox files are missing. Please try the download again.")
            }
            File(dir, ".download_complete").writeText("downloaded")
            onProgress(DownloadProgress("Chatterbox voice", 100, "Chatterbox model assets downloaded."))
        } finally {
            voiceBusy.set(false)
        }
    }

    private fun downloadFile(
        source: String,
        destination: File,
        label: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NexusLauncher-Assistant")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                destination.delete()
                throw IllegalStateException("The model download could not start. Please try again later.")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0L) {
                            ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        onProgress(DownloadProgress(label, percent, "Downloading"))
                    }
                    output.fd.sync()
                }
            }
            if (!destination.isFile || destination.length() == 0L) {
                destination.delete()
                throw IllegalStateException("The downloaded file was empty. Please try again.")
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
