package app.lawnchair.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.compose.material3.RadioButton
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var voicePlayer: MediaPlayer? = null
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
                var showRequiredDownload by remember { mutableStateOf(false) }
                var modelState by remember { mutableStateOf(AssistantModelStore.localState(this)) }
                var voiceState by remember { mutableStateOf(AssistantModelStore.voiceState(this)) }
                var localRuntimeReady by remember { mutableStateOf(LocalAssistantEngine.isLoaded) }
                var downloading by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableIntStateOf(0) }
                var downloadLabel by remember { mutableStateOf("") }
                var downloadMessage by remember { mutableStateOf("") }
                var downloadError by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    modelState = AssistantModelStore.localState(this@NexusAssistantActivity)
                    voiceState = AssistantModelStore.voiceState(this@NexusAssistantActivity)
                    localRuntimeReady = if (modelState == AssistantModelStore.AssetState.DOWNLOADED) {
                        withContext(Dispatchers.IO) {
                            LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                        }
                    } else false
                    // A missing optional local model or neural voice must not block cloud and phone actions.
                    showRequiredDownload = modelState != AssistantModelStore.AssetState.DOWNLOADED ||
                        !localRuntimeReady
                }

                var showCloudSettings by remember { mutableStateOf(false) }
                var selectedProvider by remember { mutableStateOf(NexusCloudAssistant.provider(this@NexusAssistantActivity)) }

                NexusAssistantScreen(
                    cloudSignedIn = NexusCloudAssistant.isSignedIn(this@NexusAssistantActivity),
                    selectedProvider = selectedProvider,
                    onCloudSettings = { showCloudSettings = true },
                    onBack = ::finish,
                    onListen = { onRecognized ->
                        speechResultHandler = { recognized ->
                            processInput(recognized, onRecognized)
                        }
                        output = onRecognized
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) startSpeechRecognition()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCommand = { query, setResponse ->
                        output = setResponse
                        processInput(query, setResponse)
                    },
                    onSpeak = ::speak,
                )

                if (showCloudSettings) {
                    AlertDialog(
                        onDismissRequest = { showCloudSettings = false },
                        title = { Text("Online AI settings") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    if (NexusCloudAssistant.isSignedIn(this@NexusAssistantActivity)) {
                                        "Your Nexus account session is available for online AI."
                                    } else {
                                        "Sign in to your Nexus account in the app to enable online AI. The assistant uses your signed-in Supabase session."
                                    }
                                )
                                Text("AI provider")
                                listOf("gemini" to "Gemini", "openai" to "OpenAI", "anthropic" to "Anthropic").forEach { (id, label) ->
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selectedProvider == id,
                                            onClick = {
                                                selectedProvider = id
                                                NexusCloudAssistant.setProvider(this@NexusAssistantActivity, id)
                                            },
                                        )
                                        TextButton(onClick = {
                                            selectedProvider = id
                                            NexusCloudAssistant.setProvider(this@NexusAssistantActivity, id)
                                        }) { Text(label) }
                                    }
                                }
                                Text(
                                    "Provider availability depends on the API keys and plan configured on the Nexus server. API secrets stay on the server and are not stored in the launcher."
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCloudSettings = false }) { Text("Done") }
                        },
                    )
                }

                if (showRequiredDownload) {
                    AlertDialog(
                        onDismissRequest = { if (!downloading) showRequiredDownload = false },
                        title = { Text("Download required models") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("This assistant needs to download local model first.")
                                Text("This feature is required to download local model and local voice first.")
                                Text(
                                    if (voiceState == AssistantModelStore.AssetState.DOWNLOADED)
                                        "Chatterbox voice has already been downloaded."
                                    else "Chatterbox high-quality voice is not downloaded yet."
                                )
                                Text(
                                    when {
                                        modelState != AssistantModelStore.AssetState.DOWNLOADED ->
                                            "Local chat model is not downloaded yet."
                                        localRuntimeReady -> "Local chat model has already been downloaded and loaded."
                                        else -> "Local chat model is downloaded, but could not be loaded. Tap Download to retry."
                                    }
                                )
                                if (downloading) {
                                    Text(downloadLabel)
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(downloadMessage)
                                }
                                downloadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Text("Chatterbox is a large download (over 1 GB for the selected Q4 files). Connect to Wi-Fi and keep enough free storage. Downloaded model assets do not mean Chatterbox speech playback is active yet.")
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !downloading,
                                onClick = {
                                    downloading = true
                                    downloadError = null
                                    downloadProgress = 0
                                    lifecycleScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                AssistantModelStore.downloadRequired(this@NexusAssistantActivity) { progress ->
                                                    runOnUiThread {
                                                        downloadLabel = progress.label
                                                        downloadProgress = progress.percent
                                                        downloadMessage = progress.message
                                                    }
                                                }
                                            }
                                            modelState = AssistantModelStore.localState(this@NexusAssistantActivity)
                                            voiceState = AssistantModelStore.voiceState(this@NexusAssistantActivity)
                                            localRuntimeReady = withContext(Dispatchers.IO) {
                                                LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                                            }
                                            if (modelState == AssistantModelStore.AssetState.DOWNLOADED && localRuntimeReady) {
                                                showRequiredDownload = false
                                                downloadMessage = if (voiceState == AssistantModelStore.AssetState.DOWNLOADED) {
                                                    "Local chat is ready. Chatterbox voice assets are present and will be used when the device can load them."
                                                } else {
                                                    "Local chat is ready. You can download the optional Chatterbox voice later from TTS & Voice settings."
                                                }
                                            } else {
                                                downloadError = if (!localRuntimeReady) {
                                                    "The local chat model could not be loaded on this device. You can still use phone commands or sign in for online AI."
                                                } else {
                                                    "The local model download is incomplete. Please try again."
                                                }
                                            }
                                        } catch (error: Exception) {
                                            downloadError = error.message ?: "The download could not finish. Please try again."
                                        } finally {
                                            downloading = false
                                        }
                                    }
                                },
                            ) { Text(if (downloading) "Downloading…" else "Download") }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !downloading,
                                onClick = { showRequiredDownload = false },
                            ) { Text("Later") }
                        },
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("hi", "IN")
    }

    private fun speak(message: String) {
        if (!ttsReady || message.isBlank()) return

        // Chatterbox is an English voice. Keep Hindi and other scripts on the device TTS voice.
        val useChatterbox = message.all { it.code < 128 } &&
            AssistantModelStore.voiceState(this) == AssistantModelStore.AssetState.DOWNLOADED

        if (!useChatterbox) {
            speakWithPhoneVoice(message)
            return
        }

        lifecycleScope.launch {
            val audioFile = withContext(Dispatchers.IO) {
                runCatching {
                    ChatterboxOnnxEngine.synthesizeToWav(this@NexusAssistantActivity, message)
                }.getOrNull()
            }
            if (isFinishing || isDestroyed) {
                audioFile?.delete()
                return@launch
            }
            if (audioFile != null) {
                playChatterboxAudio(audioFile, message)
            } else {
                speakWithPhoneVoice(message)
            }
        }
    }

    private fun speakWithPhoneVoice(message: String) {
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nexus-assistant-fallback")
        }
    }

    private fun playChatterboxAudio(file: java.io.File, fallbackText: String) {
        runCatching {
            voicePlayer?.setOnPreparedListener(null)
            voicePlayer?.setOnCompletionListener(null)
            voicePlayer?.setOnErrorListener(null)
            voicePlayer?.release()
            voicePlayer = null

            val player = MediaPlayer()
            voicePlayer = player
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                runCatching { it.start() }.onFailure {
                    cleanupGeneratedAudio(file)
                    speakWithPhoneVoice(fallbackText)
                }
            }
            player.setOnCompletionListener {
                cleanupGeneratedAudio(file)
            }
            player.setOnErrorListener { _, _, _ ->
                cleanupGeneratedAudio(file)
                speakWithPhoneVoice(fallbackText)
                true
            }
            player.prepareAsync()
        }.onFailure {
            cleanupGeneratedAudio(file)
            speakWithPhoneVoice(fallbackText)
        }
    }

    private fun cleanupGeneratedAudio(file: java.io.File) {
        runCatching {
            voicePlayer?.setOnPreparedListener(null)
            voicePlayer?.setOnCompletionListener(null)
            voicePlayer?.setOnErrorListener(null)
            voicePlayer?.release()
        }
        voicePlayer = null
        file.delete()
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

    private fun isSystemCommand(raw: String): Boolean {
        val command = raw.trim().lowercase(Locale.ROOT)
        return command.contains("flashlight") || command.contains("torch") ||
            command.contains("टॉर्च") || command.contains("फ्लैशलाइट") ||
            command.contains("wifi") || command.contains("wi-fi") ||
            command.contains("वाई फाई") || command.contains("वाई-फाई") || command.contains("वाईफाई") ||
            command.startsWith("search ") || command.startsWith("सर्च ") ||
            command.startsWith("खोज ") || command.startsWith("ढूंढो ") || command.startsWith("खोजो ") ||
            command.startsWith("open ") || command.startsWith("खोलो ") ||
            command.startsWith("launch ") || command.startsWith("चलाओ ") || command.startsWith("start ") ||
            command.contains("settings") || command.contains("सेटिंग") || command.contains("सेटिंग्स") ||
            command.contains("bluetooth") || command.contains("ब्लूटूथ") ||
            command.contains("mobile data") || command.contains("मोबाइल डेटा") ||
            command.contains("internet") || command.contains("इंटरनेट") ||
            command.contains("alarm") || command.contains("अलार्म") ||
            command.contains("timer") || command.contains("टाइमर") ||
            command.contains("time") || command.contains("समय")
    }

    private fun processInput(raw: String, onResult: (String) -> Unit) {
        if (isSystemCommand(raw)) {
            val answer = handleCommand(raw)
            onResult(answer)
            speak(answer)
            return
        }
        lifecycleScope.launch {
            var cloudError: String? = null
            val cloudAnswer = withContext(Dispatchers.IO) {
                runCatching { NexusCloudAssistant.reply(this@NexusAssistantActivity, raw) }
                    .onFailure { cloudError = it.message }
                    .getOrNull()
            }
            if (!cloudAnswer.isNullOrBlank()) {
                onResult(cloudAnswer)
                speak(cloudAnswer)
                return@launch
            }

            val localAnswer = withContext(Dispatchers.IO) {
                if (!LocalAssistantEngine.isLoaded) {
                    LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                }
                if (LocalAssistantEngine.isLoaded) {
                    runCatching { LocalAssistantEngine.reply(raw) }.getOrNull()
                } else null
            }
            if (!localAnswer.isNullOrBlank()) {
                onResult(localAnswer)
                speak(localAnswer)
            } else {
                val fallback = cloudError?.takeIf { it.contains("Sign in") || it.contains("premium", ignoreCase = true) }
                    ?: "I couldn't reach the online assistant or load the local model. Please check your internet connection, or use a phone command such as ‘open YouTube’."
                onResult(fallback)
                speak(fallback)
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


    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        runCatching { voicePlayer?.release() }
        voicePlayer = null
        ChatterboxOnnxEngine.release()
        LocalAssistantEngine.release()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun NexusAssistantScreen(
    cloudSignedIn: Boolean,
    selectedProvider: String,
    onCloudSettings: () -> Unit,
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
            Text("Nexus Assistant combines phone actions, local chat and optional online AI.")
            Text(if (cloudSignedIn) "Online AI: account connected." else "Online AI: sign in to your Nexus account to connect.")
            Text("AI provider: " + when (selectedProvider) {
                "openai" -> "OpenAI"
                "anthropic" -> "Anthropic"
                else -> "Gemini"
            })
            Button(onClick = onCloudSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Online AI settings")
            }
            Text("Voice: Android text-to-speech is active. Chatterbox voice assets can be downloaded from the required-model dialog; its neural playback pipeline is not connected yet.")
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
