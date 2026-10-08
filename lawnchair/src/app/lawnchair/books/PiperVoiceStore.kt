package app.lawnchair.books

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class PiperVoice(
    val id: String,
    val language: String,
    val displayName: String,
    val quality: String,
    val modelPath: String,
) {
    val baseUrl: String
        get() = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/$modelPath"
    val configPath: String
        get() = "$modelPath.json"
}

data class InstalledPiperVoice(
    val voice: PiperVoice,
    val modelFile: File,
    val configFile: File,
)

object PiperVoiceCatalog {
    val voices = listOf(
        PiperVoice("hi_IN-pratham-medium", "Hindi", "Pratham", "Medium", "hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx"),
        PiperVoice("hi_IN-priyamvada-medium", "Hindi", "Priyamvada", "Medium", "hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx"),
        PiperVoice("en_US-amy-medium", "English (US)", "Amy", "Medium", "en/en_US/amy/medium/en_US-amy-medium.onnx"),
        PiperVoice("en_US-lessac-medium", "English (US)", "Lessac", "Medium", "en/en_US/lessac/medium/en_US-lessac-medium.onnx"),
        PiperVoice("en_US-joe-medium", "English (US)", "Joe", "Medium", "en/en_US/joe/medium/en_US-joe-medium.onnx"),
        PiperVoice("en_US-hfc_male-medium", "English (US)", "HFC Male", "Medium", "en/en_US/hfc_male/medium/en_US-hfc_male-medium.onnx"),
        PiperVoice("en_GB-alan-medium", "English (UK)", "Alan", "Medium", "en/en_GB/alan/medium/en_GB-alan-medium.onnx"),
        PiperVoice("en_GB-cori-high", "English (UK)", "Cori", "High", "en/en_GB/cori/high/en_GB-cori-high.onnx"),
        PiperVoice("de_DE-thorsten-high", "German", "Thorsten", "High", "de/de_DE/thorsten/high/de_DE-thorsten-high.onnx"),
        PiperVoice("fr_FR-siwis-medium", "French", "Siwis", "Medium", "fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx"),
        PiperVoice("es_ES-sharvard-medium", "Spanish", "Sharvard", "Medium", "es/es_ES/sharvard/medium/es_ES-sharvard-medium.onnx"),
    )
}

class PiperVoiceManager(context: Context) {
    private val root = File(context.filesDir, "piper-voices").apply { mkdirs() }

    fun installed(): List<InstalledPiperVoice> = PiperVoiceCatalog.voices.mapNotNull { voice ->
        val model = File(root, "${voice.id}.onnx")
        val config = File(root, "${voice.id}.onnx.json")
        if (model.isFile && config.isFile && model.length() > 0 && config.length() > 0) {
            InstalledPiperVoice(voice, model, config)
        } else null
    }

    fun isInstalled(voice: PiperVoice): Boolean {
        val model = File(root, "${voice.id}.onnx")
        val config = File(root, "${voice.id}.onnx.json")
        return model.isFile && model.length() > 0 && config.isFile && config.length() > 0
    }

    fun delete(voice: PiperVoice) {
        File(root, "${voice.id}.onnx").delete()
        File(root, "${voice.id}.onnx.json").delete()
    }

    fun download(
        voice: PiperVoice,
        onProgress: (Int) -> Unit = {},
    ) {
        downloadFile(voice.baseUrl, File(root, "${voice.id}.onnx"), onProgress)
        downloadFile(voice.baseUrl + ".json", File(root, "${voice.id}.onnx.json")) { }
    }

    private fun downloadFile(
        source: String,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            destination.delete()
            throw IllegalStateException("Voice download failed: HTTP ${connection.responseCode}")
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
                    if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                }
                output.fd.sync()
            }
        }
        if (!destination.isFile || destination.length() == 0L) {
            destination.delete()
            throw IllegalStateException("Empty voice file")
        }
    }
}
