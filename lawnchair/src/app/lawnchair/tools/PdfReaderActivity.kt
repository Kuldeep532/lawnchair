package app.lawnchair.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PdfReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { PdfReaderScreen(::finish) } }
    }
}

@Composable
private fun PdfReaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extracted by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { selected ->
        if (selected == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                selected,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        uri = selected
        page = 0
        extracted = ""
        message = null
    }

    fun render(source: Uri, targetPage: Int) {
        working = true
        Thread {
            val result = runCatching {
                val descriptor = context.contentResolver.openFileDescriptor(source, "r")
                    ?: error("PDF could not be opened.")
                descriptor.use {
                    val renderer = PdfRenderer(it.fileDescriptor)
                    renderer.use {
                        pageCount = renderer.pageCount
                        val pdfPage = renderer.openPage(
                            targetPage.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0)),
                        )
                        pdfPage.use {
                            val scale = 2f
                            val image = Bitmap.createBitmap(
                                (it.width * scale).toInt().coerceAtLeast(1),
                                (it.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            it.render(
                                image,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            image
                        }
                    }
                }
            }
            context.mainExecutor.execute {
                working = false
                result.onSuccess {
                    bitmap = it
                    message = "Page " + (targetPage + 1) + " of " + pageCount + "."
                }.onFailure {
                    bitmap = null
                    message = "This PDF page could not be opened."
                }
            }
        }.start()
    }

    fun ocr() {
        val image = bitmap ?: return
        working = true
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(image, 0))
            .addOnSuccessListener {
                extracted = it.text
                message = if (it.text.isBlank()) {
                    "No readable text was found on this page."
                } else {
                    "Text extracted from this page."
                }
                working = false
                recognizer.close()
            }
            .addOnFailureListener {
                message = "Text could not be extracted from this page."
                working = false
                recognizer.close()
            }
    }

    fun reset() {
        uri = null
        pageCount = 0
        page = 0
        bitmap = null
        extracted = ""
        message = null
    }

    fun shareText() {
        if (extracted.isBlank()) return
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, extracted)
        context.startActivity(
            android.content.Intent.createChooser(send, "Share extracted text"),
        )
    }

    BackHandler {
        if (uri != null || bitmap != null || extracted.isNotBlank()) reset() else onBack()
    }

    LaunchedEffect(uri, page) {
        uri?.let { render(it, page) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Reader") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uri != null || bitmap != null || extracted.isNotBlank()) {
                            reset()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (uri == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Read PDF pages and extract text with OCR.")
                Button(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open PDF")
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "PDF page",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else if (working) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Opening page…")
                    }
                }

                Text("Page " + (if (pageCount == 0) 0 else page + 1) + " of " + pageCount)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { page = (page - 1).coerceAtLeast(0) },
                        enabled = page > 0 && !working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Previous")
                    }
                    OutlinedButton(
                        onClick = {
                            page = (page + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
                        },
                        enabled = page + 1 < pageCount && !working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Next")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = ::ocr,
                        enabled = bitmap != null && !working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ImageSearch, contentDescription = null)
                        Text("Read Text")
                    }
                    OutlinedButton(
                        onClick = ::reset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Close PDF")
                    }
                }

                if (extracted.isNotBlank()) {
                    Text("Extracted text")
                    Text(extracted, Modifier.fillMaxWidth().weight(0.8f))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(extracted))
                            },
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = ::shareText) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                        }
                    }
                }

                message?.let { Text(it) }
            }
        }
    }
}
