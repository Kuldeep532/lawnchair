package app.lawnchair.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.CalendarContract
import java.util.Calendar
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import androidx.core.content.ContextCompat
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
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
                NexusAssistantScreen(
                    onOpenAiGallery = {
                        startActivity(NexusAssistantLauncher.aiGalleryIntent(this@NexusAssistantActivity))
                    },
                    onOpenSearch = {
                        startActivity(NexusSearchBrowserActivity.openSearchScreen(this@NexusAssistantActivity))
                    },
                    onBack = ::finish,
                    onListen = { onRecognized ->
                        speechResultHandler = { recognized -> processInput(recognized, onRecognized) }
                        output = onRecognized
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startSpeechRecognition()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onCommand = { query, setResponse ->
                        output = setResponse
                        processInput(query, setResponse)
                    },
                )
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

    private fun isSystemCommand(raw: String): Boolean {
        val command = raw.trim().lowercase(Locale.ROOT)
        return command.contains("flashlight") || command.contains("torch") ||
            command.contains("टॉर्च") || command.contains("फ्लैशलाइट") ||
            command.contains("wifi") || command.contains("wi-fi") ||
            command.contains("वाई फाई") || command.contains("वाई-फाई") || command.contains("वाईफाई") ||
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
        val command = raw.trim()
        if (command.isBlank()) {
            onResult("Type or say what you need.")
            return
        }
        val normalized = command.lowercase(Locale.ROOT)

        // Live information is looked up online; an offline model must never invent today's weather.
        if (normalized.contains("weather") || normalized.contains("मौसम")) {
            onResult(openSearch(command))
            return
        }

        // Create an event through the phone's calendar app. Android asks the user to review
        // and save it there; this avoids silently writing to a calendar.
        if (normalized.startsWith("add ") || normalized.startsWith("schedule ") ||
            normalized.startsWith("remind me") || normalized.contains("याद दिलाओ") ||
            normalized.contains("शेड्यूल")) {
            onResult(createCalendarEvent(command))
            return
        }

        if (isSystemCommand(command)) {
            onResult(handleCommand(command))
            return
        }

        // General questions stay in the same assistant. Use the on-device model when available;
        // otherwise offer a web search instead of pretending cloud chat is already connected here.
        lifecycleScope.launch {
            val localAnswer = withContext(Dispatchers.IO) {
                runCatching {
                    if (LocalAssistantEngine.isLoaded || LocalAssistantEngine.loadIfAvailable(this@NexusAssistantActivity)) {
                        LocalAssistantEngine.reply(command)
                    } else null
                }.getOrNull()
            }
            onResult(localAnswer ?: openSearch(command))
        }
    }

    private fun createCalendarEvent(raw: String): String {
        val timeMatch = Regex("(?:at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)", RegexOption.IGNORE_CASE)
            .find(raw)
        if (timeMatch == null) return "Tell me the time, for example: Add drinking water at 7:30 PM."
        val hourInput = timeMatch.groupValues[1].toIntOrNull() ?: return "Please say the time again."
        val minute = timeMatch.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
        if (hourInput !in 1..12 || minute !in 0..59) return "Please provide a valid time."
        val isPm = timeMatch.groupValues[3].lowercase(Locale.ROOT).startsWith("p")
        val hour = (hourInput % 12) + if (isPm) 12 else 0
        val beforeTime = raw.substring(0, timeMatch.range.first)
        val afterTime = raw.substring(timeMatch.range.last + 1)
            .trim().trimStart(',', '-', ':').replace(Regex("^(add|remind me to)\\s*", RegexOption.IGNORE_CASE), "").trim()
        val title = (if (afterTime.isNotBlank()) afterTime else beforeTime)
            .replace(Regex("^(add|schedule|schedule for|remind me to|remind me|याद दिलाओ|शेड्यूल)\\s*", RegexOption.IGNORE_CASE), "")
            .trim().ifBlank { "Reminder" }
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, 15) }
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, "Added with Nexus Assistant")
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
            startActivity(intent)
            "Your reminder is ready. Review it and tap Save in your calendar."
        }.getOrElse {
            "I could not open the calendar on this phone. Please check that a calendar app is installed."
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
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
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

        if (!musicQuery.isNullOrBlank()) return openMusicAppChooser(musicQuery)
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

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun NexusAssistantScreen(
    onOpenAiGallery: () -> Unit,
    onOpenSearch: () -> Unit,
    onBack: () -> Unit,
    onListen: ((String) -> Unit) -> Unit,
    onCommand: (String, (String) -> Unit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("Hi, how can I help?") }
    var showAttach by remember { mutableStateOf(false) }
    var showOnlineAi by remember { mutableStateOf(false) }
    var onlineWebView by remember { mutableStateOf<WebView?>(null) }
    BackHandler(onBack = {
        if (showOnlineAi) showOnlineAi = false else onBack()
    })

    if (showOnlineAi) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Assistant") },
                    navigationIcon = {
                        IconButton(onClick = { showOnlineAi = false }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Assistant")
                        }
                    },
                )
            },
        ) { padding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(padding),
                factory = { context ->
                    NexusChatBrowserActivity.createChatWebView(context).also { onlineWebView = it }
                },
                update = { onlineWebView = it },
            )
        }
        DisposableEffect(Unit) {
            onDispose {
                onlineWebView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    removeAllViews()
                    destroy()
                }
                onlineWebView = null
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Ask a question or tell me what to do.",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Assistant", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    Text(response, modifier = Modifier.padding(top = 8.dp))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                placeholder = { Text("Ask anything or give a command") },
                minLines = 2,
                maxLines = 5,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { showAttach = true }) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach")
                }
                IconButton(onClick = { onListen { response = it } }) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Speak")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val message = query
                        query = ""
                        onCommand(message) { response = it }
                    },
                    enabled = query.isNotBlank(),
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null)
                    Text(" Send")
                }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(onDismissRequest = { showAttach = false }) {
            Text(
                "Add to your message",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = { showAttach = false; onOpenSearch() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Search the web") }
            TextButton(
                onClick = { showAttach = false; onOpenAiGallery() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("On-device AI settings") }
            TextButton(
                onClick = { showAttach = false; showOnlineAi = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Online AI") }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}
