package app.lawnchair.tools

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

private data class StudioTool(
    val name: String,
    val description: String,
    val category: String,
    val destination: (android.content.Context) -> Intent,
)

class ToolsStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                ToolsStudioScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun ToolsStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tools = listOf(
        StudioTool("QR Code Generator", "Create QR codes for text, websites, Wi-Fi and UPI.", "Everyday tools") {
            Intent(it, QrGeneratorActivity::class.java)
        },
        StudioTool("Password Generator", "Create a strong password on this device.", "Everyday tools") {
            Intent(it, PasswordGeneratorActivity::class.java)
        },
        StudioTool("Image Compressor", "Reduce image size without uploading your image.", "Images and documents") {
            Intent(it, ImageCompressorActivity::class.java)
        },
        StudioTool("Image to PDF", "Turn selected images into a PDF document.", "Images and documents") {
            Intent(it, ImageToPdfActivity::class.java)
        },
        StudioTool("PDF to Images", "Save PDF pages as image files.", "Images and documents") {
            Intent(it, PdfToImageActivity::class.java)
        },
        StudioTool("PDF Reader", "Open and read PDF documents.", "Images and documents") {
            Intent(it, PdfReaderActivity::class.java)
        },
        StudioTool("Image OCR", "Read text from an image and copy or share it.", "Images and documents") {
            Intent(it, TextExtractorActivity::class.java)
        },
        StudioTool("Online Radio", "Discover stations and listen to online radio.", "Media") {
            Intent(it, app.lawnchair.radio.OnlineRadioActivity::class.java)
        },
        StudioTool("Live TV", "Browse and watch available live channels.", "Media") {
            Intent(it, app.lawnchair.livetv.LiveTvActivity::class.java)
        },
        StudioTool("TTS and Voice Settings", "Manage available text-to-speech voices.", "Media") {
            Intent(it, app.lawnchair.books.BooksVoiceSettingsActivity::class.java)
        },
        StudioTool("Nexus Assistant", "Ask questions and use assistant features.", "AI and focus") {
            app.lawnchair.assistant.NexusAssistantLauncher.intent(it)
        },
        StudioTool("AI Gallery", "Manage available on-device AI models.", "AI and focus") {
            app.lawnchair.assistant.NexusAssistantLauncher.aiGalleryIntent(it)
        },
        StudioTool("Digital Wellbeing", "Review app use and manage focus limits.", "AI and focus") {
            Intent(it, app.lawnchair.wellbeing.DigitalWellbeingActivity::class.java)
        },
    )

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to launcher")
                    }
                },
                title = { Text("Tool Studio") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val categories = tools.map { it.category }.distinct()
            categories.forEach { category ->
                item(key = "heading-$category") {
                    Text(
                        category,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(tools.filter { it.category == category }, key = { it.name }) { tool ->
                    Card(
                        onClick = {
                            context.startActivity(tool.destination(context))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(tool.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
