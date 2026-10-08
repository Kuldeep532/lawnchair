package app.lawnchair.tools

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.rounded.ImageSearch
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
}

@Composable
private fun TextExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Extractor") },
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
            Text("Take text from an image on your phone. Your image stays on the device.")

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
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(extractedText))
                        message = "Text copied."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("Copy Text")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Select an image to get started.")
            }
        }
    }
}
