package app.lawnchair.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.Locale

/**
 * Nexus Assistant v1.
 *
 * The assistant never downloads executable code. It uses Android speech recognition
 * for input and Android TextToSpeech for spoken output while a licence-compatible neural
 * voice asset and its inference runtime are evaluated.
 */
class NexusAssistantActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var output: ((String) -> Unit)? = null
    private var speechResultHandler: ((String) -> Unit)? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) speechResultHandler?.invoke(spoken)
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
                        speechResultHandler = { recognized ->
                            val answer = handleCommand(recognized)
                            onRecognized(answer)
                            speak(answer)
                        }
                        output = onRecognized
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
                )
            }
        }
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
    }.getOrElse {
        // Some devices can block torch access while another camera app is active.
        "The flashlight could not be changed right now. Close apps using the camera and try again."
    }

    private fun openInternetPanel(): String = runCatching {
        startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        "Internet controls opened. You can choose Wi-Fi or mobile data there."
    }.recoverCatching {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }.fold(
        onSuccess = { "Internet settings opened. Choose Wi-Fi there." },
        onFailure = { "I could not open Internet settings on this device." },
    )

    private fun openSearch(rawQuery: String): String {
        val query = rawQuery.trim()
        if (query.isBlank()) return "Tell me what you want to search for."
        return runCatching {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
            "Searching the web for $query."
        }.getOrElse {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.google.com/search?q=" + Uri.encode(query)
                )))
                "Searching the web for $query."
            }.getOrElse {
                "Search could not be opened. Please try again."
            }
        }
    }

    private fun openAppOrSearch(appName: String): String {
        val term = appName.trim()
        if (term.isBlank()) return "Tell me which app to open."
        val normalized = term.lowercase(Locale.ROOT)
        val aliases = mapOf(
            "youtube" to "com.google.android.youtube",
            "यूट्यूब" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "क्रोम" to "com.android.chrome",
            "settings" to "com.android.settings",
            "सेटिंग्स" to "com.android.settings",
            "phone" to "com.google.android.dialer",
            "फोन" to "com.google.android.dialer",
            "camera" to "com.android.camera2",
            "कैमरा" to "com.android.camera2",
            "google maps" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "मैप्स" to "com.google.android.apps.maps",
        )
        val packageName = aliases[normalized]
        val intent = if (packageName != null) {
            packageManager.getLaunchIntentForPackage(packageName)
        } else {
            packageManager.getLaunchIntentForPackage(term)
        }
        if (intent != null) {
            return runCatching {
                startActivity(intent)
                "Opening $term."
            }.getOrElse { "I could not open $term. Please try again." }
        }
        return openSearch(term)
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
    onListen: ((String) -> Unit) -> Unit,
    onCommand: (String, (String) -> Unit) -> Unit,
    onSpeak: (String) -> Unit,
) {
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
            Text("Free assistant controls with web search and spoken replies. Neural voice downloads will be enabled after model licensing and offline playback are verified.")
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
                onClick = { onListen { response = it } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
                Text("  Speak")
            }
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
