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
            Text(
                "Manage the AI features available on this device.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Offline AI Assistant", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            localRuntimeReady -> "Ready. You can use the assistant without an internet connection."
                            modelState == AssistantModelStore.AssetState.DOWNLOADED ->
                                "The model is downloaded. Prepare it to start offline AI."
                            modelState == AssistantModelStore.AssetState.PARTIAL ->
                                "The previous download was incomplete. Download it again to continue."
                            else -> "Download the local model to use AI on this device."
                        },
                    )
                    if (downloading) {
                        if (downloadLabel.isNotBlank()) Text(downloadLabel)
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (downloadMessage.isNotBlank()) Text(downloadMessage)
                    }
                    if (!downloadError.isNullOrBlank()) {
                        Text(downloadError, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = onDownloadChat,
                        enabled = !downloading && !localRuntimeReady,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                downloading -> "Preparing…"
                                localRuntimeReady -> "Ready for offline use"
                                modelState == AssistantModelStore.AssetState.DOWNLOADED -> "Start offline AI"
                                else -> "Download and prepare model"
                            },
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Device Voice", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Books Reader and PDF Reader use the speech voices installed on your Android device. " +
                            "Available languages and offline voice data depend on your phone.",
                    )
                    Button(
                        onClick = onVoiceSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Manage device voices")
                    }
                }
            }
            Text(
                "Chatterbox, Coqui and Piper are not available as working on-device voice engines in this version. " +
                    "They are not shown as installed or ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
