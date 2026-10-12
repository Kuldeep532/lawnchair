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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag().ifBlank { "hi-IN" })
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Nexus Assistant")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    output?.invoke("Voice input is not available right now. Voice input settings opened.")
                }.onFailure {
                    output?.invoke("Voice input is not available on this device. You can still type a command.")
                }
            }
    }

    private fun handleCommand(raw: String): String {
        val command = raw.trim().lowercase(Locale.ROOT)
        if (command.isBlank()) return "Please say or type a command."

        return when {
            command.contains("flashlight") || command.contains("torch") ||
                command.contains("टॉर्च") || command.contains("फ्लैशलाइट") -> toggleFlashlight()
            command.contains("wifi") || command.contains("wi-fi") ||
                command.contains("वाई फाई") || command.contains("वाई-फाई") ||
                command.contains("वाईफाई") -> openInternetPanel()
            command.startsWith("search ") || command.startsWith("सर्च ") ||
                command.startsWith("खोज ") || command.startsWith("ढूंढो ") ||
                command.startsWith("खोजो ") -> openSearch(raw.substringAfter(' ').trim())
            command.startsWith("open ") || command.startsWith("खोलो ") ||
                command.startsWith("launch ") || command.startsWith("चलाओ ") ||
                command.startsWith("start ") -> {
                val term = when {
                    command.startsWith("open ") -> raw.substringAfter("open ").trim()
                    command.startsWith("खोलो ") -> raw.substringAfter("खोलो ").trim()
                    command.startsWith("launch ") -> raw.substringAfter("launch ").trim()
                    command.startsWith("चलाओ ") -> raw.substringAfter("चलाओ ").trim()
                    else -> raw.substringAfter("start ").trim()
                }
                val launch = resolveInstalledApp(term)
                if (launch != null) runCatching {
                    startActivity(launch)
                    "Opening $term."
                }.getOrElse { "I could not open $term. Please try again." }
                else openSearch(term)
            }
            command.contains("settings") || command.contains("सेटिंग") ||
                command.contains("सेटिंग्स") -> runCatching {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    "Opening settings."
                }.getOrElse { "I could not open settings on this device." }
            command.contains("bluetooth") || command.contains("ब्लूटूथ") -> runCatching {
                startActivity(Intent(Settings.Panel.ACTION_BLUETOOTH))
                "Bluetooth controls opened."
            }.getOrElse {
                runCatching {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    "Bluetooth settings opened."
                }.getOrElse { "I could not open Bluetooth settings on this device." }
            }
            command.contains("mobile data") || command.contains("मोबाइल डेटा") ||
                command.contains("internet") || command.contains("इंटरनेट") -> openInternetPanel()
            command.contains("alarm") || command.contains("अलार्म") -> runCatching {
                startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                "Opening alarms."
            }.getOrElse { "I could not open alarms on this device." }
            command.contains("timer") || command.contains("टाइमर") -> runCatching {
                startActivity(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, 60))
                "Opening a one-minute timer."
            }.getOrElse { "I could not open the timer on this device." }
            command.contains("time") || command.contains("समय") ->
                "The time is " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
            else -> openSearch(raw)
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

    private fun resolveInstalledApp(label: String): Intent? {
        val normalized = label.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null
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
        if (packageName != null) {
            packageManager.getLaunchIntentForPackage(packageName)?.let { return it }
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { it.loadLabel(packageManager).toString().trim().lowercase(Locale.ROOT) == normalized }
            ?.activityInfo
            ?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(it.packageName, it.name) }
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
        "The flashlight could not be changed right now. Close apps using the camera and try again."
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
            Text("Free assistant with voice input, spoken replies, web search and quick phone actions.")
            Text("Voice: Android text-to-speech is active. Hugging Face model downloads will be offered after a compatible speech engine and redistribution licence are verified.")
            Text("Try commands like: search weather, open YouTube, turn on flashlight, open Wi-Fi settings, open Bluetooth settings, set a timer, or what time is it?")
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
