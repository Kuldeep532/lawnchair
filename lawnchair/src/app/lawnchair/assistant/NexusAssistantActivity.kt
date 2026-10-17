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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
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
 * for input; spoken playback is intentionally reserved for Books Reader and PDF Reader.
 */
class NexusAssistantActivity : ComponentActivity() {
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
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                var showRequiredDownload by remember {
                    mutableStateOf(intent.getBooleanExtra(EXTRA_OPEN_MODELS, false) || AssistantModelStore.localState(this@NexusAssistantActivity) != AssistantModelStore.AssetState.DOWNLOADED)
                }
                var modelState by remember { mutableStateOf(AssistantModelStore.localState(this)) }
                var localRuntimeReady by remember { mutableStateOf(LocalAssistantEngine.isLoaded) }
                var downloading by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableIntStateOf(0) }
                var downloadLabel by remember { mutableStateOf("") }
                var downloadMessage by remember { mutableStateOf("") }
                var downloadError by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    modelState = AssistantModelStore.localState(this@NexusAssistantActivity)
                    localRuntimeReady = if (modelState == AssistantModelStore.AssetState.DOWNLOADED) {
                        withContext(Dispatchers.IO) {
                            LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                        }
                    } else false
                }

                if (showRequiredDownload) {
                    AiGalleryScreen(
                        modelState = modelState,
                        localRuntimeReady = localRuntimeReady,
                        downloading = downloading,
                        downloadProgress = downloadProgress,
                        downloadLabel = downloadLabel,
                        downloadMessage = downloadMessage,
                        downloadError = downloadError,
                        onBack = ::finish,
                        onDownloadChat = {
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
                                    localRuntimeReady = withContext(Dispatchers.IO) {
                                        LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                                    }
                                    if (!localRuntimeReady) downloadError = "The local chat model could not be loaded on this device."
                                    else downloadMessage = "Local AI is ready for offline chat."
                                } catch (error: Exception) {
                                    downloadError = error.message ?: "The download could not finish. Please try again."
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        onVoiceSettings = {
                            runCatching { startActivity(Intent(Settings.ACTION_TTS_SETTINGS)) }
                        },
                        onOpenReader = {
                            runCatching { startActivity(Intent(this@NexusAssistantActivity, app.lawnchair.books.BooksActivity::class.java)) }
                        },
                    )
                } else {
                    NexusAssistantScreen(
                        onOpenModels = {
                            startActivity(NexusAssistantLauncher.modelsIntent(this@NexusAssistantActivity))
                        },
                        onBack = ::finish,
                        onListen = { onRecognized ->
                            speechResultHandler = { recognized -> processInput(recognized, onRecognized) }
                            output = onRecognized
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeechRecognition()
                            else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onCommand = { query, setResponse ->
                            output = setResponse
                            processInput(query, setResponse)
                        },
                    )
                }


            }
        }
    }

    companion object {
        const val EXTRA_OPEN_MODELS = "app.lawnchair.assistant.OPEN_MODELS"
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

    private fun isDirectWebQuestion(raw: String): Boolean {
        val query = raw.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return false
        return query.startsWith("what is ") ||
            query.startsWith("what's ") ||
            query.startsWith("who is ") ||
            query.startsWith("who was ") ||
            query.startsWith("where is ") ||
            query.startsWith("when is ") ||
            query.startsWith("when was ") ||
            query.startsWith("why is ") ||
            query.startsWith("how to ") ||
            query.startsWith("how do ") ||
            query.startsWith("how does ") ||
            query.startsWith("tell me about ") ||
            query.startsWith("latest news ") ||
            query.startsWith("समझाओ ") ||
            query.startsWith("क्या है ") ||
            query.startsWith("कौन है ") ||
            query.startsWith("कहाँ है ") ||
            query.startsWith("कैसे ") ||
            query.startsWith("बताओ ")
    }

    private fun isSystemCommand(raw: String): Boolean {
        val command = raw.trim().lowercase(Locale.ROOT)
        return command.contains("flashlight") || command.contains("torch") ||
            command.contains("टॉर्च") || command.contains("फ्लैशलाइट") ||
            command.contains("wifi") || command.contains("wi-fi") ||
            command.contains("वाई फाई") || command.contains("वाई-फाई") || command.contains("वाईफाई") ||
            command.startsWith("search ") || command.startsWith("play music ") || command.startsWith("सर्च ") ||
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
        if (raw.trim().equals("models", ignoreCase = true) ||
            raw.trim().equals("download models", ignoreCase = true) ||
            raw.trim().equals("download voice ai models", ignoreCase = true)
        ) {
            startActivity(NexusAssistantLauncher.modelsIntent(this@NexusAssistantActivity))
            return
        }
        // Common information questions should work even when no local AI model or API is available.
        if (isDirectWebQuestion(raw)) {
            onResult(openSearch(raw.trim()))
            return
        }
        if (isSystemCommand(raw)) {
            val answer = handleCommand(raw)
            onResult(answer)
            return
        }
        lifecycleScope.launch {
            val localAnswer = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!LocalAssistantEngine.isLoaded) {
                    LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)
                }
                if (LocalAssistantEngine.isLoaded) {
                    runCatching { LocalAssistantEngine.reply(raw) }.getOrNull()
                } else null
            }
            if (!localAnswer.isNullOrBlank()) {
                onResult(localAnswer)
            } else {
                onResult("The local chat model is not ready on this device. Open Settings, then Models, to download Voice AI Models. Phone commands remain available.")
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
            command.startsWith("play music ") -> openMusicAppChooser(raw.substringAfter("play music ").trim())
            command.startsWith("search ") || command.startsWith("सर्च ") ||
                command.startsWith("खोज ") || command.startsWith("ढूंढो ") ||
                command.startsWith("खोजो ") -> openUniversalSearch(raw)
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

    /**
     * One API-free search entry point. Prefixes let users choose the destination:
     * "search music <title>", "search video <title>", "search apps <name>".
     * A plain "search <query>" uses Google web search.
     */
    private fun openMusicAppChooser(rawQuery: String): String {
        val query = rawQuery.trim()
        if (query.isBlank()) return "Type a song or artist name first."
        val playIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
            putExtra(android.app.SearchManager.QUERY, query)
            putExtra(android.provider.MediaStore.EXTRA_MEDIA_TITLE, query)
        }
        return runCatching {
            startActivity(Intent.createChooser(playIntent, "Select music app to play"))
            "Choose an installed music app to play $query."
        }.getOrElse {
            openUrlSearch(
                "https://music.youtube.com/search?q=" + Uri.encode(query),
                "No compatible music app was found, so I opened a music search for $query."
            )
        }
    }

    private fun openUniversalSearch(rawCommand: String): String {
        val query = rawCommand.trim()
            .replaceFirst(Regex("^(search|सर्च|खोज|ढूंढो|खोजो)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        if (query.isBlank()) return "Type what you want to search for."

        val lower = query.lowercase(Locale.ROOT)
        val musicQuery = when {
            lower.startsWith("music ") -> query.substringAfter(' ').trim()
            lower.startsWith("music:") -> query.substringAfter(':').trim()
            lower.startsWith("म्यूजिक ") -> query.substringAfter(' ').trim()
            else -> null
        }
        val videoQuery = when {
            lower.startsWith("video ") -> query.substringAfter(' ').trim()
            lower.startsWith("video:") -> query.substringAfter(':').trim()
            lower.startsWith("videos ") -> query.substringAfter(' ').trim()
            lower.startsWith("वीडियो ") -> query.substringAfter(' ').trim()
            else -> null
        }
        val appQuery = when {
            lower.startsWith("apps ") -> query.substringAfter(' ').trim()
            lower.startsWith("app ") -> query.substringAfter(' ').trim()
            lower.startsWith("apps:") -> query.substringAfter(':').trim()
            else -> null
        }

        if (!musicQuery.isNullOrBlank()) return openUrlSearch(
            "https://music.youtube.com/search?q=" + Uri.encode(musicQuery),
            "Searching music for $musicQuery."
        )
        if (!videoQuery.isNullOrBlank()) return openUrlSearch(
            "https://www.youtube.com/results?search_query=" + Uri.encode(videoQuery),
            "Searching videos for $videoQuery."
        )
        if (!appQuery.isNullOrBlank()) {
            val launch = resolveInstalledApp(appQuery)
            if (launch != null) return runCatching {
                startActivity(launch)
                "Opening $appQuery."
            }.getOrElse { "I could not open $appQuery." }
            return openSearch(appQuery + " app")
        }
        return openSearch(query)
    }

    private fun openUrlSearch(url: String, successMessage: String): String = runCatching {
        startActivity(NexusSearchBrowserActivity.urlIntent(this, url))
        successMessage
    }.getOrElse { "Search could not be opened. Please try again." }

    private fun openSearch(rawQuery: String): String {
        val query = rawQuery.trim()
        if (query.isBlank()) return "Tell me what you want to search for."
        return runCatching {
            startActivity(NexusSearchBrowserActivity.queryIntent(this, query))
            "Searching the web for $query in Nexus Search."
        }.getOrElse {
            "Search could not be opened. Please try again."
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
        LocalAssistantEngine.release()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun NexusAssistantScreen(
    onOpenModels: () -> Unit,
    onBack: () -> Unit,
    onListen: ((String) -> Unit) -> Unit,
    onCommand: (String, (String) -> Unit) -> Unit,
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
            Text("Nexus Assistant is local-first: phone commands and downloaded local chat work without signing in.")
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
                onClick = { onCommand("search $query") { response = it } },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("Search the web") }
            Button(
                onClick = { onCommand("play music $query") { response = it } },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("Play with a music app") }
            Button(
                onClick = { onCommand("search video $query") { response = it } },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("Search videos") }
            Button(
                onClick = { onCommand("open $query") { response = it } },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("Find or open an app") }
            Button(
                onClick = { onListen { response = it } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
                Text("  Speak")
            }
            Button(
                onClick = onOpenModels,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open AI Gallery · Download models") }
            Text("Assistant response")
            Text(response)
            Text("Note: Android may ask for microphone permission. Wi-Fi opens the system Internet panel because Android does not allow ordinary apps to switch Wi-Fi directly.")
        }
    }
}
