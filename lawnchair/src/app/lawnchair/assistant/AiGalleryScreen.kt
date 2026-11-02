package app.lawnchair.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AiGalleryScreen(
    modelState: AssistantModelStore.AssetState,
    localRuntimeReady: Boolean,
    downloading: Boolean,
    downloadProgress: Int,
    downloadLabel: String,
    downloadMessage: String,
    downloadError: String?,
    onBack: () -> Unit,
    onDownloadChat: () -> Unit,
    onVoiceSettings: () -> Unit,
    onOpenReader: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Gallery") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Manage AI assets currently supported by Nexus Launcher. Model downloads are available from the Assistant model manager. Unsupported voice engines are not shown as installed.", style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local AI Chat Model", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            localRuntimeReady -> "Ready for offline chat."
                            modelState == AssistantModelStore.AssetState.DOWNLOADED -> "The model file is present, but an offline inference engine is not connected yet."
                            modelState == AssistantModelStore.AssetState.PARTIAL -> "An incomplete download was found. Download again to finish setup."
                            else -> "Not downloaded. Download this model before using offline AI chat."
                        },
                    )
                    if (downloading) {
                        Text(downloadLabel)
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(downloadMessage)
                    }
                    if (!downloadError.isNullOrBlank()) {
                        Text(downloadError, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onDownloadChat,
                        enabled = !downloading && !localRuntimeReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (downloading) "Downloading…" else if (localRuntimeReady) "Downloaded and ready" else "Download local AI model") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local Voice", style = MaterialTheme.typography.titleMedium)
                    Text("Books Reader and PDF Reader use Android's installed text-to-speech voice. Voice availability depends on the device and downloaded language data.")
                    Button(onClick = onVoiceSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage and download voice")
                    }
                    OutlinedButton(onClick = onOpenReader, modifier = Modifier.fillMaxWidth()) {
                        Text("Open voice settings")
                    }
                }
            }
            Text(
                "Chatterbox, Coqui and Piper inference are not connected in this build. This gallery links to working device settings and the existing local model manager.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
