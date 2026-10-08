package app.lawnchair.tools

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

private data class SelectedFile(val uri: Uri, val name: String, val mime: String?)

class FileRenamerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { FileRenamerScreen(::finish) } }
    }
}

@Composable
private fun FileRenamerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<SelectedFile>() }
    var prefix by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        selected.clear()
        uris.forEach { uri ->
            selected += SelectedFile(
                uri = uri,
                name = FileRenamer.fileName(context.contentResolver, uri),
                mime = context.contentResolver.getType(uri),
            )
        }
        message = null
    }

    fun rename() {
        if (selected.isEmpty()) return
        working = true
        message = null
        Thread {
            val result = runCatching {
                FileRenamer.rename(
                    context,
                    selected,
                    prefix,
                    replacement,
                )
            }
            context.mainExecutor.execute {
                working = false
                result.onSuccess { count ->
                    message = "$count file" + if (count == 1) " renamed." else "s renamed."
                    selected.clear()
                }.onFailure {
                    message = "The files could not be renamed. Please try again."
                }
            }
        }.start()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rename Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Tools Studio")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Select files and give them a consistent name.")
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text(if (selected.isEmpty()) "Select Files" else "Change Selection")
            }

            if (selected.isNotEmpty()) {
                Text(selected.size.toString() + " file" + if (selected.size == 1) " selected" else "s selected")
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selected) { file ->
                        Card(Modifier.fillMaxWidth()) {
                            Text(file.name, Modifier.padding(14.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Add text at the start") },
                    placeholder = { Text("Example: Trip") },
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Replace text in the name") },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { prefix = ""; replacement = "" },
                        enabled = !working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear")
                    }
                    Button(
                        onClick = ::rename,
                        enabled = !working && (prefix.isNotBlank() || replacement.isNotBlank()),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (working) "Renaming…" else "Rename Files")
                    }
                }
            } else {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Select Files")
                }
            }

            message?.let { Text(it) }
        }
    }
}

private object FileRenamer {

    fun fileName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "File"

    fun rename(
        context: Context,
        files: List<SelectedFile>,
        prefix: String,
        replacement: String,
    ): Int {
        var count = 0
        files.forEach { file ->
            val original = file.name
            val dot = original.lastIndexOf('.')
            val base = if (dot > 0) original.substring(0, dot) else original
            val extension = if (dot > 0) original.substring(dot) else ""
            val newBase = (prefix + base.replace(replacement, "")).trim()
            val newName = newBase.ifBlank { base } + extension

            if (file.mime?.startsWith("image/") == true) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, newName)
                }
                count += context.contentResolver.update(file.uri, values, null, null)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName)
                }
                count += context.contentResolver.update(file.uri, values, null, null)
            }
        }
        return count
    }
}
