package app.lawnchair.assistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads model assets to app-private storage. A completed file download is not equivalent
 * to a model being ready for inference; the runtime must load the model before enabling chat
 * or neural speech output.
 */
object AssistantModelStore {
    const val LOCAL_MODEL_REPO = "mobiletransformers/SmolLM2-135M-Instruct"
    const val CHATTERBOX_REPO = "onnx-community/chatterbox-ONNX"

    // This manifest belongs to the Android-specific package; the SDK is currently distributed
    // as a locally-published AAR and must be integrated separately before this model can run.
    private const val LOCAL_MODEL_MANIFEST_URL =
        "https://huggingface.co/mobiletransformers/SmolLM2-135M-Instruct/resolve/main/manifest.json"

    private val chatterboxFiles = listOf(
        "config.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "generation_config.json",
        "preprocessor_config.json",
        "onnx/conditional_decoder.onnx",
        "onnx/conditional_decoder.onnx_data",
        "onnx/embed_tokens.onnx",
        "onnx/embed_tokens.onnx_data",
        "onnx/language_model_q4.onnx",
        "onnx/language_model_q4.onnx_data",
        "onnx/speech_encoder.onnx",
        "onnx/speech_encoder.onnx_data",
        "onnx/speech_tokenizer.onnx",
    )

    enum class AssetState { MISSING, PARTIAL, DOWNLOADED }
    data class DownloadProgress(val label: String, val percent: Int, val message: String)

    private val localBusy = AtomicBoolean(false)
    private val voiceBusy = AtomicBoolean(false)

    fun localDirectory(context: Context): File =
        File(context.filesDir, "assistant-models/smollm2-135m").apply { mkdirs() }

    fun voiceDirectory(context: Context): File =
        File(context.filesDir, "assistant-models/chatterbox-onnx").apply { mkdirs() }

    fun localState(context: Context): AssetState {
        val dir = localDirectory(context)
        val manifest = File(dir, "manifest.json")
        val complete = File(dir, ".manifest_download_complete")
        return when {
            complete.isFile && manifest.isFile && manifest.length() > 0L -> AssetState.DOWNLOADED
            dir.listFiles()?.any { it.isFile && it.length() > 0L } == true -> AssetState.PARTIAL
            else -> AssetState.MISSING
        }
    }

    fun voiceState(context: Context): AssetState {
        val dir = voiceDirectory(context)
        val complete = File(dir, ".download_complete")
        val essentials = listOf("config.json", "tokenizer.json", "generation_config.json")
        val allEssentialsPresent = essentials.all {
            File(dir, it).isFile && File(dir, it).length() > 0L
        }
        return when {
            complete.isFile && allEssentialsPresent -> AssetState.DOWNLOADED
            dir.walkTopDown().any { it.isFile && it.length() > 0L } -> AssetState.PARTIAL
            else -> AssetState.MISSING
        }
    }

    fun downloadRequired(context: Context, onProgress: (DownloadProgress) -> Unit) {
        downloadLocalModelManifest(context, onProgress)
        downloadChatterboxVoice(context, onProgress)
    }

    fun downloadLocalModelManifest(context: Context, onProgress: (DownloadProgress) -> Unit) {
        check(localBusy.compareAndSet(false, true)) { "The local model download is already in progress." }
        try {
            val dir = localDirectory(context)
            val temp = File(dir, "manifest.json.part")
            downloadFile(LOCAL_MODEL_MANIFEST_URL, temp, "Local assistant model", onProgress)
            val manifest = File(dir, "manifest.json")
            if (!temp.renameTo(manifest)) {
                temp.copyTo(manifest, overwrite = true)
                temp.delete()
            }
            File(dir, ".manifest_download_complete").writeText("manifest-downloaded")
            onProgress(DownloadProgress("Local assistant model", 100, "Model manifest downloaded."))
        } finally {
            localBusy.set(false)
        }
    }

    fun downloadChatterboxVoice(context: Context, onProgress: (DownloadProgress) -> Unit) {
        check(voiceBusy.compareAndSet(false, true)) { "The voice download is already in progress." }
        try {
            val dir = voiceDirectory(context)
            val base = "https://huggingface.co/$CHATTERBOX_REPO/resolve/main/"
            var completed = 0
            for (path in chatterboxFiles) {
                val target = File(dir, path)
                if (target.isFile && target.length() > 0L) {
                    completed++
                    continue
                }
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, target.name + ".part")
                downloadFile(base + path, partial, "Chatterbox voice") { update ->
                    val overall = (((completed * 100) + update.percent) / chatterboxFiles.size).coerceIn(0, 100)
                    onProgress(DownloadProgress("Chatterbox voice", overall, "Downloading " + target.name))
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                completed++
                onProgress(DownloadProgress("Chatterbox voice", completed * 100 / chatterboxFiles.size, "Downloaded " + target.name))
            }
            File(dir, ".download_complete").writeText("model-assets-downloaded")
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
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NexusLauncher-Assistant")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                destination.delete()
                throw IllegalStateException("Download could not start. Please try again later.")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0L) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
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
