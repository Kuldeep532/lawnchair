package app.lawnchair.books

import android.content.Intent
import android.media.MediaPlayer
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
import androidx.lifecycle.lifecycleScope
import app.lawnchair.assistant.ElevenLabsVoiceClient
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class BooksVoiceSettingsActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var voicePlayer: MediaPlayer? = null
    private var voiceReady by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Choose a voice for Books Reader and PDF Reader.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        textToSpeech = TextToSpeech(this, this)
        setContent {
            LawnchairTheme {
                VoiceSettingsScreen(
                    voiceReady = voiceReady,
                    initialProvider = ReaderVoicePreferences.provider(this@BooksVoiceSettingsActivity),
                    statusMessage = statusMessage,
                    onBack = ::finish,
                    onProviderChange = { ReaderVoicePreferences.setProvider(this@BooksVoiceSettingsActivity, it) },
                    onTestVoice = ::testVoice,
                    onOpenSystemSettings = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_TEXT_TO_SPEECH_SETTINGS))
                        }.onFailure {
                            statusMessage = "Android voice settings could not be opened."
                        }
                    },
                )
            }
        }
    }

    private fun testVoice(provider: String) {
        val sample = "This is a voice preview for your book and PDF reader."
        if (provider == ReaderVoicePreferences.LOCAL) {
            val engine = textToSpeech
            if (voiceReady && engine != null) {
                statusMessage = "Playing the installed Android voice."
                engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "reader-voice-preview")
            } else {
                statusMessage = "The phone voice is not ready. Check Android TTS settings and install an offline voice if needed."
            }
            return
        }

        statusMessage = "Connecting to ElevenLabs…"
        lifecycleScope.launch {
            val audioUrl = withContext(Dispatchers.IO) {
                runCatching { ElevenLabsVoiceClient.synthesize(this@BooksVoiceSettingsActivity, sample) }
                    .getOrElse { error ->
                        statusMessage = error.message ?: "ElevenLabs voice is unavailable."
                        null
                    }
            }
            if (isFinishing || isDestroyed) return@launch
            if (audioUrl.isNullOrBlank()) return@launch
            runCatching {
                voicePlayer?.release()
                val player = MediaPlayer()
                voicePlayer = player
                player.setDataSource(audioUrl)
                player.setOnPreparedListener {
                    statusMessage = "Playing ElevenLabs preview."
                    it.start()
                }
                player.setOnCompletionListener {
                    it.release()
                    if (voicePlayer === it) voicePlayer = null
                    statusMessage = "ElevenLabs preview finished."
                }
                player.setOnErrorListener { failed, _, _ ->
                    failed.release()
                    if (voicePlayer === failed) voicePlayer = null
                    statusMessage = "ElevenLabs audio could not be played. Try the local voice."
                    true
                }
                player.prepareAsync()
            }.onFailure {
                voicePlayer?.release()
                voicePlayer = null
                statusMessage = "ElevenLabs preview could not start. Try the local voice."
            }
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
        runCatching { voicePlayer?.stop() }
        runCatching { voicePlayer?.release() }
        voicePlayer = null
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
            Text("These settings do not control Nexus Assistant. Choose the voice once and use it in either reader.")

            Text("Voice provider", style = MaterialTheme.typography.titleLarge)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedProvider == ReaderVoicePreferences.ELEVENLABS,
                    onClick = {
                        selectedProvider = ReaderVoicePreferences.ELEVENLABS
                        onProviderChange(ReaderVoicePreferences.ELEVENLABS)
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("ElevenLabs online voice")
                    Text("Requires internet, a signed-in Nexus account, server configuration, and an eligible plan/credits.", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()
            Text(
                if (selectedProvider == ReaderVoicePreferences.LOCAL) {
                    if (voiceReady) "Local voice status: ready." else "Local voice status: not ready yet."
                } else {
                    "ElevenLabs status is checked when you test or start reading. If unavailable, the reader falls back to the phone voice."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onTestVoice(selectedProvider) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test selected voice") }
            if (selectedProvider == ReaderVoicePreferences.LOCAL) {
                TextButton(
                    onClick = onOpenSystemSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Manage Android voices") }
            }
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
