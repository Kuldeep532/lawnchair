package app.lawnchair.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

/** Native entry point for managing local AI assets and Android voice settings. */
class AiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                AiGalleryScreen(
                    modelState = AssistantModelStore.localState(this),
                    localRuntimeReady = false,
                    downloading = false,
                    downloadProgress = 0,
                    downloadLabel = "",
                    downloadMessage = "",
                    downloadError = null,
                    onBack = ::finish,
                    onDownloadChat = {
                        startActivity(NexusAssistantLauncher.modelsIntent(this))
                        finish()
                    },
                    onVoiceSettings = {
                        runCatching { startActivity(Intent(Settings.ACTION_TTS_SETTINGS)) }
                            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    },
                    onOpenReader = {
                        startActivity(Intent(Settings.ACTION_TTS_SETTINGS))
                    },
                )
            }
        }
    }
}
