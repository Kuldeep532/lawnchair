package app.lawnchair.assistant

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.Locale

/**
 * Nexus Assistant v1.
 *
 * The assistant never downloads executable code. It can download an openly hosted TTS
 * voice/model into app-owned storage and uses Android's speech recognizer for input.
 * A neural voice runtime can be wired in later without increasing the base APK.
 */
class NexusAssistantActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var output: ((String) -> Unit)? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) output?.invoke(spoken)
    }

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSpeechRecognition()
        else output?.invoke("Microphone permission is needed for voice input.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        tts = TextToSpeech(this, this)
        setContent {
            LawnchairTheme {
                NexusAssistantScreen(
                    onBack = ::finish,
                    onListen = { onRecognized ->
                        output = { recognized ->
                            val answer = handleCommand(recognized)
                            onRecognized(answer)
                            speak(answer)
                        }
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) startSpeechRecognition()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCommand = { query, setResponse ->
                        output = setResponse
                        val response = handleCommand(query)
                        setResponse(response)
                        speak(response)
                    },
                    onSpeak = ::speak,
                    onDownloadVoice = ::downloadVoiceModel,
                )
            }
        }
        // Start downloading the optional voice model as soon as the assistant opens.
        downloadVoiceModel()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("hi", "IN")
    }

    private fun speak(message: String) {
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nexus-assistant")
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Nexus Assistant")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { output?.invoke("Voice input is not available on this device.") }
    }

    private fun handleCommand(raw: String): String {
        val command = raw.trim().lowercase(Locale.ROOT)
        if (command.isBlank()) return "Please say or type a command."

        return when {
            command.contains("flashlight") || command.contains("torch") ||
                command.contains("टॉर्च") || command.contains("फ्लैशलाइट") -> toggleFlashlight()
            command.contains("wifi") || command.contains("wi-fi") ||
                command.contains("वाई फाई") || command.contains("वाई-फाई") -> {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                "Internet settings opened. Choose Wi-Fi there."
            }
            command.startsWith("search ") || command.startsWith("सर्च ") ||
                command.startsWith("खोज ") || command.startsWith("ढूंढो ") -> {
                val q = raw.substringAfter(' ').trim()
                val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
                runCatching { startActivity(search) }.getOrElse {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/search?q=" + Uri.encode(q)
                    )))
                }
                "Searching the web for $q"
            }
            command.startsWith("open ") || command.startsWith("खोलो ") -> {
                val term = raw.substringAfter(' ').trim()
                val launch = packageManager.getLaunchIntentForPackage(term)
                if (launch != null) {
                    startActivity(launch)
                    "Opening $term"
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/search?q=" + Uri.encode(term)
                    )))
                    "I could not find that app by name, so I searched for it."
                }
            }
            command.contains("settings") || command.contains("सेटिंग") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                "Opening settings."
            }
            command.contains("time") || command.contains("समय") ->
                "The time is " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
            else -> {
                val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", raw)
                runCatching { startActivity(search) }.getOrElse {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/search?q=" + Uri.encode(raw)
                    )))
                }
                "I searched the web for: $raw"
            }
        }
    }

    private fun toggleFlashlight(): String = runCatching {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true
        } ?: return "This device does not have a supported flashlight."
        val current = getPreferences(MODE_PRIVATE).getBoolean("flash_on", false)
        manager.setTorchMode(cameraId, !current)
        getPreferences(MODE_PRIVATE).edit().putBoolean("flash_on", !current).apply()
        if (!current) "Flashlight turned on." else "Flashlight turned off."
    }.getOrElse { "I could not change the flashlight. Check camera permission and try again." }

    private fun downloadVoiceModel() {
        val prefs = getSharedPreferences("nexus_assistant", MODE_PRIVATE)
        val modelName = "hi_IN-pratham-medium.onnx"
        val configName = "$modelName.json"
        val downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val modelFile = java.io.File(downloads, modelName)
        val configFile = java.io.File(downloads, configName)

        // Resume-friendly behavior: ask Android Download Manager only for missing assets.
        if (prefs.getBoolean("voice_model_requested", false) &&
            modelFile.exists() && modelFile.length() > 0L &&
            configFile.exists() && configFile.length() > 0L
        ) return

        val modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx"
        val configUrl = "$modelUrl.json"
        runCatching {
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            if (!modelFile.exists() || modelFile.length() == 0L) {
                val request = DownloadManager.Request(Uri.parse(modelUrl))
                    .setTitle("Nexus Assistant Hindi voice")
                    .setDescription("Downloading the Hindi voice model from Hugging Face.")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, modelName)
                manager.enqueue(request)
            }
            if (!configFile.exists() || configFile.length() == 0L) {
                val configRequest = DownloadManager.Request(Uri.parse(configUrl))
                    .setTitle("Nexus Assistant Hindi voice settings")
                    .setDescription("Downloading voice settings needed by the speech engine.")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, configName)
                manager.enqueue(configRequest)
            }
            prefs.edit().putBoolean("voice_model_requested", true).apply()
        }.onFailure {
            // Do not mark the model as requested if the device cannot enqueue downloads.
            prefs.edit().putBoolean("voice_model_requested", false).apply()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun NexusAssistantScreen(
    onBack: () -> Unit,
    onListen: (String) -> Unit,
    onCommand: (String, (String) -> Unit) -> Unit,
    onSpeak: (String) -> Unit,
    onDownloadVoice: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var response by remember {
        mutableStateOf("Hello! I'm Nexus Assistant. You can ask me to search the web, turn on the flashlight, open settings, or use voice input.")
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexus Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Free on-device controls with web search. Voice model resources download when you open the assistant.")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ask Nexus Assistant") },
                placeholder = { Text("For example: turn on flashlight") },
                minLines = 2,
            )
            Button(
                onClick = {
                    onCommand(query) { response = it }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("Send command") }
            Button(
                onClick = { onListen(query) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
                Text("  Speak")
            }
            Button(
                onClick = { onDownloadVoice() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download voice model again") }
            Text("Assistant response")
            Text(response)
            Button(
                onClick = { onSpeak(response) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Read response aloud") }
            Text("Note: Android may ask for microphone permission. Wi-Fi opens the system Internet panel because Android does not allow ordinary apps to switch Wi-Fi directly.")
        }
    }
}
