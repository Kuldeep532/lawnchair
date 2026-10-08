package app.lawnchair.tools

import android.content.ContentValues
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextExtractorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { TextExtractorScreen(::finish) } }
    }

    fun incomingImageUri(): Uri? = when (intent.action) {
        Intent.ACTION_SEND, Intent.ACTION_VIEW -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            ?: intent.data
        else -> null
    }
}

@Composable
private fun TextExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        val incomingUri = (context as? TextExtractorActivity)?.incomingImageUri()
        if (incomingUri != null && incomingUri.toString().isNotBlank()) {
            selectedUri = incomingUri
            message = "Image ready. Tap Extract Text."
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        extractedText = ""
        message = null
    }

    fun extract() {
        val uri = selectedUri ?: return
        working = true
        message = null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        runCatching {
            InputImage.fromFilePath(context, uri)
        }.onSuccess { image ->
            recognizer.process(image)
                .addOnSuccessListener {
                    extractedText = it.text
                    working = false
                    message = if (it.text.isBlank()) {
                        "No readable text was found."
                    } else {
                        "Text extracted successfully."
                    }
                    recognizer.close()
                }
                .addOnFailureListener {
                    working = false
                    message = "Text could not be extracted from this image."
                    recognizer.close()
                }
        }.onFailure {
            working = false
            message = "The selected image could not be opened."
            recognizer.close()
        }
    }

    fun clearResult() {
        selectedUri = null
        extractedText = ""
        message = null
    }

    fun shareText() {
        if (extractedText.isBlank()) return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, extractedText)
        context.startActivity(Intent.createChooser(send, "Share extracted text"))
    }

    fun saveAsPdf() {
        if (extractedText.isBlank()) return
        runCatching {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f }
            var y = 36f
            extractedText.split("\\n").forEach { line ->
                if (y > 810f) return@forEach
                canvas.drawText(line.take(80), 24f, y, paint)
                y += 20f
            }
            document.finishPage(page)
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "Nexus_Image_OCR_${System.currentTimeMillis()}.pdf")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/Nexus Plus/Image OCR")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("Could not create PDF.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    ?: error("Could not save PDF.")
                val done = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, done, null, null)
                message = "PDF saved."
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            } finally {
                document.close()
            }
        }.onFailure {
            message = "PDF could not be saved."
        }
    }

    BackHandler { if (selectedUri != null || extractedText.isNotBlank() || message != null) clearResult() else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image OCR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Extract text from an image. Your image stays on the device.")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                Text(if (selectedUri == null) "Select Image" else "Change Image")
            }

            Button(
                onClick = ::extract,
                enabled = selectedUri != null && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (working) "Reading…" else "Extract Text")
            }

            message?.let { Text(it) }

            if (extractedText.isNotBlank()) {
                OutlinedTextField(
                    value = extractedText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Extracted text") },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(extractedText))
                            message = "Text copied."
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = ::shareText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = "Share")
                        Text("Share")
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = ::saveAsPdf,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save PDF")
                    }
                    OutlinedButton(
                        onClick = ::clearResult,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear")
                        Text("Clear")
                    }
                    IconButton(onClick = ::clearResult) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(if (selectedUri == null) "Select an image to get started." else "Your image is ready to read.")
            }
        }
    }
}
