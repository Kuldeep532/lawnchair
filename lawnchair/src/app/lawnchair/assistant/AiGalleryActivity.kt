package app.lawnchair.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** AI Gallery manages the local model and the voices supplied by Android. */
class AiGalleryActivity : ComponentActivity() {
    private var modelState by androidx.compose.runtime.mutableStateOf(
        AssistantModelStore.AssetState.MISSING,
    )
    private var runtimeReady by androidx.compose.runtime.mutableStateOf(false)
    private var downloading by androidx.compose.runtime.mutableStateOf(false)
    private var progress by androidx.compose.runtime.mutableIntStateOf(0)
    private var progressLabel by androidx.compose.runtime.mutableStateOf("")
    private var progressMessage by androidx.compose.runtime.mutableStateOf("")
    private var downloadError by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelState = AssistantModelStore.localState(this)
        lifecycleScope.launch {
            runtimeReady = withContext(Dispatchers.IO) {
                LocalAssistantEngine.loadIfAvailable(this@AiGalleryActivity)
            }
        }
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                AiGalleryScreen(
                    modelState = modelState,
                    localRuntimeReady = runtimeReady,
                    downloading = downloading,
                    downloadProgress = progress,
                    downloadLabel = progressLabel,
                    downloadMessage = progressMessage,
                    downloadError = downloadError,
                    onBack = ::finish,
                    onDownloadChat = ::prepareLocalAssistant,
                    onVoiceSettings = ::openVoiceSettings,
                )
            }
        }
    }

    private fun prepareLocalAssistant() {
        if (downloading || runtimeReady) return
        downloading = true
        downloadError = null
        progress = 0
        lifecycleScope.launch {
            try {
                if (modelState != AssistantModelStore.AssetState.DOWNLOADED) {
                    withContext(Dispatchers.IO) {
                        AssistantModelStore.downloadLocalModel(this@AiGalleryActivity) { update ->
                            runOnUiThread {
                                progressLabel = update.label
                                progress = update.percent
                                progressMessage = update.message
                            }
                        }
                    }
                }
                modelState = AssistantModelStore.localState(this@AiGalleryActivity)
                val ready = withContext(Dispatchers.IO) {
                    LocalAssistantEngine.loadIfAvailable(this@AiGalleryActivity)
                }
                runtimeReady = ready
                if (ready) {
                    progress = 100
                    progressMessage = "Offline AI is ready to use."
                } else {
                    downloadError = "The model is saved, but offline AI could not start on this device. Please try again."
                }
            } catch (_: Exception) {
                modelState = AssistantModelStore.localState(this@AiGalleryActivity)
                downloadError = "The model could not be prepared. Check your internet connection and try again."
            } finally {
                downloading = false
            }
        }
    }

    private fun openVoiceSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_TTS_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}
