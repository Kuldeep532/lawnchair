package app.lawnchair.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

class ImageToPdfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { ImageToPdfScreen(::finish) } }
    }
}

@Composable
private fun ImageToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        status = null
    }

    fun convert() {
        val uri = selectedUri ?: return
        working = true
        status = null
        Thread {
            val result = runCatching { ImagePdf.create(context, uri) }
            context.mainExecutor.execute {
                working = false
                result.onSuccess { status = "PDF saved to Downloads." }
                    .onFailure { status = "PDF could not be created. Please try again." }
            }
        }.start()
    }

    fun clear() {
        selectedUri = null
        status = null
    }

    BackHandler {
        if (selectedUri != null || status != null) clear() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image to PDF") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedUri != null || status != null) clear() else onBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Turn an image into a PDF directly on your phone. No website or subscription is needed.")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selectedUri == null) "Select Image" else "Change Image")
            }

            Button(
                onClick = ::convert,
                enabled = selectedUri != null && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (working) "Creating PDF…" else "Create PDF")
            }

            status?.let { Text(it) }

            if (selectedUri != null || status != null) {
                OutlinedButton(
                    onClick = ::clear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

private object ImagePdf {
    fun create(context: Context, source: Uri) {
        val bitmap = context.contentResolver.openInputStream(source).use {
            BitmapFactory.decodeStream(it)
        } ?: error("Image could not be opened.")

        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            )
            try {
                drawBitmapFit(page.canvas, bitmap, pageWidth, pageHeight)
            } finally {
                document.finishPage(page)
            }

            val values = ContentValues().apply {
                put(
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    "Nexus_Image_" + System.currentTimeMillis() + ".pdf",
                )
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    "Download/Nexus Plus/Image to PDF",
                )
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values,
            ) ?: error("Could not create the PDF file.")

            try {
                context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    ?: error("Could not save the PDF file.")
                val done = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, done, null, null)
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } finally {
            document.close()
            bitmap.recycle()
        }
    }

    private fun drawBitmapFit(
        canvas: Canvas,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val availableWidth = pageWidth - 48f
        val availableHeight = pageHeight - 48f
        val scale = minOf(
            availableWidth / bitmap.width.toFloat(),
            availableHeight / bitmap.height.toFloat(),
        )
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = (pageWidth - width) / 2f
        val top = (pageHeight - height) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(left, top, left + width, top + height),
            paint,
        )
    }
}
