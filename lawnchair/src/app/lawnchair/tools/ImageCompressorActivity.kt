package app.lawnchair.tools

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ImageCompressorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { ImageCompressorScreen(::finish) } }
    }
}

@Composable
private fun ImageCompressorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var quality by remember { mutableFloatStateOf(70f) }
    var working by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultSize by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sourceUri = uri
        fileName = ImageCompressor.fileName(context.contentResolver, uri)
        resultUri = null
        resultSize = 0L
        error = null
    }

    fun compress() {
        val uri = sourceUri ?: return
        working = true
        error = null
        Thread {
            val result = runCatching {
                ImageCompressor.compress(context, uri, quality.toInt())
            }
            context.mainExecutor.execute {
                working = false
                result.onSuccess {
                    resultUri = it.uri
                    resultSize = it.size
                }.onFailure {
                    error = it.message ?: "The image could not be compressed."
                }
            }
        }.start()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Compressor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back to Tools Studio",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Compress an image on this device without uploading it anywhere.")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sourceUri == null) "Select Image" else "Change Image")
            }

            if (sourceUri != null) {
                Text("Selected: " + fileName)
                Text("Quality: " + quality.toInt() + "%")
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f,
                    steps = 17,
                )
            }

            Button(
                onClick = ::compress,
                enabled = sourceUri != null && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (working) "Compressing…" else "Compress Image")
            }

            error?.let { Text(it) }

            resultUri?.let { uri ->
                Text(
                    "Compressed image ready. New size: " +
                        ImageCompressor.formatBytes(resultSize),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { ImageCompressor.share(context, uri) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Share")
                    }
                    Button(
                        onClick = { resultUri = null },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Compress Again")
                    }
                }
            }
        }
    }
}

private data class SavedImage(
    val uri: Uri,
    val size: Long,
)

private object ImageCompressor {

    fun fileName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "image"

    fun compress(context: Context, source: Uri, quality: Int): SavedImage {
        val bitmap = context.contentResolver.openInputStream(source).use {
            BitmapFactory.decodeStream(it)
        } ?: error("The selected image could not be opened.")

        val workDir =
            File(context.cacheDir, "nexus_plus/image_compressor").apply { mkdirs() }
        val output = File(workDir, "Nexus_Image_" + System.currentTimeMillis() + ".jpg")

        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                "The image could not be compressed."
            }
        }
        bitmap.recycle()

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/Nexus Plus/Image Compressor",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("The output image could not be created.")

        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                output.inputStream().use { input -> input.copyTo(out) }
            } ?: error("The output stream could not be opened.")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            SavedImage(uri, output.length())
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share compressed image"))
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f)
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
