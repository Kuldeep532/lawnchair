package app.lawnchair.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.io.BufferedReader
import java.io.InputStreamReader

class BooksActivity : ComponentActivity() {
    private lateinit var store: BooksStore
    private var selectedBook: BookItem? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val title = contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Book"
        val mime = contentResolver.getType(uri).orEmpty()
        store.addBook(title.substringBeforeLast('.', title), uri.toString(), mime)
        showLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        store = BooksStore.getInstance(this)

        val externalUri = intent?.data
        if (externalUri != null && isSupportedBook(externalUri)) {
            importAndOpen(externalUri)
        } else {
            showLibrary()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        val uri = intent.data
        if (uri != null && isSupportedBook(uri)) {
            importAndOpen(uri)
        }
    }

    private fun importAndOpen(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }

        val title = contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Book"

        val mimeType = contentResolver.getType(uri).orEmpty()
        val item = store.addBook(
            title.substringBeforeLast('.', title),
            uri.toString(),
            mimeType,
        )
        showReader(item)
    }

    private fun isSupportedBook(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
        if (mime in setOf("application/pdf", "application/epub+zip", "text/plain", "text/markdown")) {
            return true
        }

        val name = contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().lowercase(Locale.ROOT)

        return name.endsWith(".pdf") ||
            name.endsWith(".epub") ||
            name.endsWith(".txt") ||
            name.endsWith(".md")
    }

    private fun showLibrary() {
        selectedBook = null
        setContent {
            LawnchairTheme {
                BooksLibraryScreen(
                    books = store.books(),
                    onImport = { picker.launch(arrayOf(
                        "application/pdf",
                        "text/plain",
                        "text/markdown",
                        "application/epub+zip",
                        "application/*",
                        "text/*",
                    )) },
                    onOpen = {
                        selectedBook = it
                        showReader(it)
                    },
                    onDelete = {
                        store.removeBook(it.id)
                        showLibrary()
                    },
                )
            }
        }
    }

    private fun showReader(book: BookItem) {
        setContent {
            LawnchairTheme {
                BookReaderScreen(
                    book = book,
                    onBack = { showLibrary() },
                    onPositionChanged = { store.updatePosition(book.id, it) },
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BooksLibraryScreen(
    books: List<BookItem>,
    onImport: () -> Unit,
    onOpen: (BookItem) -> Unit,
    onDelete: (BookItem) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to library")
                    }
                },
                title = { Text(book.title, maxLines = 1) },
                actions = {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More reader options")
                    }
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Configure TTS & voice") },
                            onClick = { showMore = false },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(
                            enabled = current > 0,
                            onClick = {
                                current = (current - 1).coerceAtLeast(0)
                                onPositionChanged(current)
                            },
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(onClick = { playing = !playing }) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                            )
                        }
                        IconButton(
                            enabled = current < lines.lastIndex,
                            onClick = {
                                current = (current + 1).coerceAtMost(lines.lastIndex.coerceAtLeast(0))
                                onPositionChanged(current)
                            },
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pageNumber,
                            onValueChange = { pageNumber = it.filter(Char::isDigit) },
                            label = { Text("Go to page") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            pageNumber.toIntOrNull()?.let {
                                current = it.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
                                onPositionChanged(current)
                            }
                        }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Go")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val text = lines.getOrNull(current).orEmpty()
        Text(
            text = text.ifBlank { "Loading…" },
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
private fun loadPlainTextPages(
    context: android.content.Context,
    uri: Uri,
): List<String> {
    val text = BufferedReader(
        InputStreamReader(context.contentResolver.openInputStream(uri))
    ).use { it?.readText().orEmpty() }
    return paginateText(text)
}

private fun paginateText(text: String, charsPerPage: Int = 1800): List<String> {
    if (text.isBlank()) return emptyList()
    val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    if (normalized.length <= charsPerPage) return listOf(normalized)

    val pages = mutableListOf<String>()
    var start = 0
    while (start < normalized.length) {
        val target = minOf(start + charsPerPage, normalized.length)
        val split = normalized.lastIndexOf('\n', target)
            .takeIf { it > start + 400 }
            ?: normalized.lastIndexOf(' ', target).takeIf { it > start + 400 }
            ?: target
        pages += normalized.substring(start, split).trim()
        start = split
        while (start < normalized.length && normalized[start].isWhitespace()) start++
    }
    return pages
}
