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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.Locale

class BooksVoiceSettingsActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var voiceReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        textToSpeech = TextToSpeech(this, this)
        setContent {
            LawnchairTheme {
                VoiceSettingsScreen(
                    voiceReady = voiceReady,
                    onBack = ::finish,
                    onTestVoice = {
                        if (voiceReady) {
                            textToSpeech?.speak(
                                "This is the local offline voice for Nexus.",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "nexus-local-voice-test",
                            )
                        }
                    },
                    onOpenSystemSettings = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_TEXT_TO_SPEECH_SETTINGS))
                        }
                    },
                )
            }
        }
    }

    override fun onInit(status: Int) {
        voiceReady = status == TextToSpeech.SUCCESS
        if (voiceReady) {
            textToSpeech?.language = Locale.getDefault()
        }
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
    onBack: () -> Unit,
    onTestVoice: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("TTS & Voice") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Local voice", style = MaterialTheme.typography.titleLarge)
            Text(
                "Nexus uses the voice installed on your Android device. It does not download a multi-gigabyte voice model, and it can work offline when the selected system voice supports offline speech.",
            )
            Text(
                if (voiceReady) "The device's text-to-speech engine is ready."
                else "The device's text-to-speech engine is not ready yet. Check the system voice settings.",
            )
            Button(
                onClick = onTestVoice,
                enabled = voiceReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test local voice") }
            Button(
                onClick = onOpenSystemSettings,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Android TTS settings") }
            HorizontalDivider()
            Text("ElevenLabs", style = MaterialTheme.typography.titleLarge)
            Text(
                "For neural online speech, open Nexus Assistant and select ElevenLabs. It needs a signed-in Nexus account, server-side API configuration, and the required plan or AI credits. If it fails, the assistant falls back to the local phone voice.",
            )
        }
    }
}
