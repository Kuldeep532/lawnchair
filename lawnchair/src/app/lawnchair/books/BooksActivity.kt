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
        showLibrary()
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
                title = { Text("Books") },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.Add, contentDescription = "Import book")
                    }
                },
            )
        },
    ) { padding ->
        if (books.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.MenuBook, contentDescription = null)
                Text("Your book library is empty.")
                Text("Import a book to start reading.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(book) }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (book.lastPosition > 0) "Resume from saved position"
                                else "Not started",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = { onDelete(book) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove book")
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BookReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onPositionChanged: (Int) -> Unit,
) {
    var lines by remember(book.uri) { mutableStateOf<List<String>>(emptyList()) }
    var current by remember(book.uri) { mutableIntStateOf(book.lastPosition) }
    var playing by remember { mutableStateOf(false) }
    var pageNumber by remember { mutableStateOf("") }

    LaunchedEffect(book.uri) {
        runCatching {
            val uri = Uri.parse(book.uri)
            if (book.mimeType.startsWith("text/") || book.mimeType == "text/plain" || book.mimeType == "text/markdown") {
                lines = BufferedReader(InputStreamReader(
                    androidx.compose.ui.platform.LocalContext.current.contentResolver.openInputStream(uri)
                )).use { reader -> reader?.readLines().orEmpty() }
            } else {
                lines = listOf(
                    "This book has been imported successfully.",
                    "Full PDF and EPUB text extraction will be added in the next Books stage.",
                    "You can still open the original file using your device's file viewer.",
                )
            }
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(book.title) },
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