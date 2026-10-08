package app.lawnchair.books

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.concurrent.Executors

class BooksVoiceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        val manager = PiperVoiceManager(this)
        val executor = Executors.newSingleThreadExecutor()
        setContent {
            LawnchairTheme {
                var installed by remember { mutableStateOf(manager.installed().map { it.voice.id }.toSet()) }
                var selected by remember { mutableStateOf("Phone default TTS") }
                var downloading by remember { mutableStateOf<String?>(null) }
                var progress by remember { mutableIntStateOf(0) }
                var error by remember { mutableStateOf<String?>(null) }
                DisposableEffect(Unit) {
                    onDispose { executor.shutdownNow() }
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
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
                        Text("Download Piper voices for offline book reading.", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { selected = "Phone default TTS" }) {
                            Text(if (selected == "Phone default TTS") "Using phone default TTS" else "Use phone default TTS")
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Text("Piper voices", style = MaterialTheme.typography.titleLarge)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(PiperVoiceCatalog.voices, key = { it.id }) { voice ->
                                val installedVoice = voice.id in installed
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(voice.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text("${voice.language} • ${voice.quality}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                        when {
                                            downloading == voice.id -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                            installedVoice -> Button(onClick = { selected = voice.id }) {
                                                Text(if (selected == voice.id) "Selected" else "Use")
                                            }
                                            else -> Button(onClick = {
                                                downloading = voice.id
                                                progress = 0
                                                error = null
                                                executor.execute {
                                                    runCatching {
                                                        manager.download(voice) { value -> runOnUiThread { progress = value } }
                                                    }.onSuccess {
                                                        runOnUiThread {
                                                            installed = manager.installed().map { installedItem -> installedItem.voice.id }.toSet()
                                                            selected = voice.id
                                                            downloading = null
                                                            progress = 100
                                                        }
                                                    }.onFailure { exception ->
                                                        runOnUiThread {
                                                            downloading = null
                                                            error = exception.message ?: "Voice download failed."
                                                        }
                                                    }
                                                }
                                            }) { Text("Download") }
                                        }
                                    }
                                    if (downloading == voice.id) {
                                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}