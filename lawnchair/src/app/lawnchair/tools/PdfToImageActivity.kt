package app.lawnchair.tools

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PdfToImageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent {
            LawnchairTheme {
                PdfToImageScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun PdfToImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var pageCount by remember { mutableStateOf(0) }
    var converting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val outputFiles = remember { mutableStateListOf<OutputFile>() }
    var zipOutput by remember { mutableStateOf<OutputFile?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        runCatching {
            val count = PdfRendererInfo.pageCount(context.contentResolver, uri)
            selectedUri = uri
            selectedName = PdfFileNames.displayName(context.contentResolver, uri)
            pageCount = count
            outputFiles.clear()
            zipOutput = null
            progress = 0
            error = null
        }.onFailure {
            selectedUri = null
            pageCount = 0
            error = "The selected PDF could not be opened."
        }
    }

    fun convert() {
        val uri = selectedUri ?: return
        converting = true
        progress = 0
        error = null
        outputFiles.clear()
        zipOutput = null

        Thread {
            val result = runCatching {
                PdfImageConverter.convert(
                    context = context,
                    uri = uri,
                    onProgress = { done, total ->
                        context.mainExecutor.execute {
                            progress = if (total == 0) 0 else done * 100 / total
                        }
                    },
                )
            }
            context.mainExecutor.execute {
                converting = false
                result.onSuccess { converted ->
                    outputFiles.addAll(converted.images)
                    zipOutput = converted.zip
                    progress = 100
                }.onFailure {
                    error = it.message ?: "The PDF could not be converted."
                }
            }
        }.start()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back to Tools Studio",
                        )
                    }
                },
                title = { Text("PDF to Image Converter") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Convert PDF pages to images completely offline. Your PDF stays on this device.")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                enabled = !converting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.height(1.dp))
                Text(if (selectedUri == null) "Select PDF" else "Change PDF")
            }

            if (selectedUri != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(selectedName.ifBlank { "Selected PDF" })
                        Text("$pageCount page" + if (pageCount == 1) "" else "s")
                        Text(
                            if (pageCount == 1) {
                                "Single Image is supported for this PDF."
                            } else {
                                "Multiple pages detected. The converted images will also be packaged into a ZIP file."
                            },
                        )
                    }
                }
            }

            Button(
                onClick = ::convert,
                enabled = selectedUri != null && !converting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Convert")
            }

            if (converting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Converting… $progress%")
                }
            }

            error?.let { Text(it) }

            if (outputFiles.isNotEmpty()) {
                Text("Converted images")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(outputFiles) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.name, modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = {
                                        PdfImageConverter.share(
                                            context,
                                            item.uri,
                                            "image/png",
                                        )
                                    },
                                ) {
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }

            zipOutput?.let { zip ->
                Button(
                    onClick = {
                        PdfImageConverter.share(context, zip.uri, "application/zip")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share ZIP")
                }
            }
        }
    }
}

private data class OutputFile(
    val name: String,
    val uri: Uri,
)

private data class ConversionResult(
    val images: List<OutputFile>,
    val zip: OutputFile?,
)

private object PdfRendererInfo {
    fun pageCount(resolver: ContentResolver, uri: Uri): Int {
        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: error("The selected PDF could not be opened.")
        descriptor.use {
            PdfRenderer(it.fileDescriptor).use { renderer ->
                return renderer.pageCount
            }
        }
    }
}

private object PdfFileNames {
    fun displayName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Selected PDF"
}

private object PdfImageConverter {

    fun convert(
        context: Context,
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ConversionResult {
        val workDir = File(context.cacheDir, "nexus_plus/pdf_to_image").apply { mkdirs() }
        workDir.listFiles()?.forEach { it.delete() }

        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("The selected PDF could not be opened.")

        descriptor.use { parcel ->
            PdfRenderer(parcel.fileDescriptor).use { renderer ->
                val total = renderer.pageCount
                val renderedFiles = ArrayList<File>(total)

                for (index in 0 until total) {
                    renderer.openPage(index).use { page ->
                        val width = (page.width * 2).coerceAtLeast(1080)
                        val height = (page.height * 2).coerceAtLeast(1080)
                        val bitmap = Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.ARGB_8888,
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        val file = File(
                            workDir,
                            "page_" + (index + 1).toString().padStart(4, '0') + ".png",
                        )
                        FileOutputStream(file).use { stream ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                "The image could not be written."
                            }
                        }
                        bitmap.recycle()
                        renderedFiles += file
                    }
                    onProgress(index + 1, total)
                }

                val exportedImages = renderedFiles.map { file ->
                    exportImage(context, file)
                }

                return if (total == 1) {
                    ConversionResult(images = exportedImages, zip = null)
                } else {
                    val zipFile = File(
                        workDir,
                        "PDF_to_Image_" + System.currentTimeMillis() + ".zip",
                    )
                    ZipOutputStream(
                        BufferedOutputStream(FileOutputStream(zipFile)),
                    ).use { zip ->
                        renderedFiles.forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    ConversionResult(
                        images = exportedImages,
                        zip = exportZip(context, zipFile),
                    )
                }
            }
        }
    }

    private fun exportImage(context: Context, file: File): OutputFile =
        exportFile(
            context,
            file,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "Pictures/Nexus Plus/PDF to Image",
            "image/png",
        )

    private fun exportZip(context: Context, file: File): OutputFile =
        exportFile(
            context,
            file,
            MediaStore.Files.getContentUri("external"),
            "Download/Nexus Plus/PDF to Image",
            "application/zip",
        )

    private fun exportFile(
        context: Context,
        file: File,
        collection: Uri,
        relativePath: String,
        mimeType: String,
    ): OutputFile {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, file.name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: error("The output file could not be created.")

        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("The output stream could not be opened.")

            values.clear()
            values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            OutputFile(file.name, uri)
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    fun share(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share converted file"))
    }
}
