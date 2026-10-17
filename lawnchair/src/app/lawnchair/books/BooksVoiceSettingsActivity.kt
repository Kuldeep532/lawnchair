package app.lawnchair.books

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.Locale

class BooksVoiceSettingsActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var voiceReady by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Choose a voice for Books Reader and PDF Reader.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this, this)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                VoiceSettingsScreen(
                    voiceReady = voiceReady,
                    initialProvider = ReaderVoicePreferences.LOCAL,
                    statusMessage = statusMessage,
                    onBack = ::finish,
                    onProviderChange = { ReaderVoicePreferences.setProvider(this@BooksVoiceSettingsActivity, ReaderVoicePreferences.LOCAL) },
                    onTestVoice = ::testVoice,
                    onOpenGallery = {
                        startActivity(app.lawnchair.assistant.NexusAssistantLauncher.modelsIntent(this@BooksVoiceSettingsActivity))
                    },
                    onOpenSystemSettings = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_TTS_SETTINGS))
                        }.onFailure {
                            statusMessage = "Android voice settings could not be opened."
                        }
                    },
                )
            }
        }
    }

    private fun testVoice(provider: String) {
        val engine = textToSpeech
        if (voiceReady && engine != null) {
            statusMessage = "Playing the installed Android voice."
            engine.speak(
                "This is a voice preview for your book and PDF reader.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "reader-voice-preview",
            )
        } else {
            statusMessage = "The phone voice is not ready. Open Android voice settings and install offline voice data if needed."
        }
    }

    override fun onInit(status: Int) {
        voiceReady = status == TextToSpeech.SUCCESS
        if (voiceReady) {
            val engine = textToSpeech
            if (engine != null) {
                val preferred = Locale.getDefault()
                val result = engine.setLanguage(preferred)
                if (result < TextToSpeech.LANG_AVAILABLE) {
                    engine.language = Locale.US
                }
            }
        }
        if (!voiceReady) statusMessage = "Android's text-to-speech engine is not ready."
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun VoiceSettingsScreen(
    voiceReady: Boolean,
    initialProvider: String,
    statusMessage: String,
    onBack: () -> Unit,
    onProviderChange: (String) -> Unit,
    onTestVoice: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(initialProvider) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to reader")
                    }
                },
                title = { Text("Reader Voice") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("One voice setup for Books Reader and PDF Reader.", style = MaterialTheme.typography.titleMedium)
            Text("These settings use voice services installed on your phone. No account or online service is required.")

            Text("Local voice", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedProvider == ReaderVoicePreferences.LOCAL,
                    onClick = {
                        selectedProvider = ReaderVoicePreferences.LOCAL
                        onProviderChange(ReaderVoicePreferences.LOCAL)
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local Android voice")
                    Text("Uses the voice installed on this phone. No large model download.", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Text(
                if (voiceReady) "Local voice status: ready." else "Local voice status: not ready yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onTestVoice(selectedProvider) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test selected voice") }
            Button(
                onClick = onOpenGallery,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open AI Gallery") }
            TextButton(
                onClick = onOpenSystemSettings,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Manage Android voices") }
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
