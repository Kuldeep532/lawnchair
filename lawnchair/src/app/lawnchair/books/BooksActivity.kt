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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
        if (externalUri != null && intent?.action == Intent.ACTION_VIEW && isSupportedBook(externalUri)) {
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
        if (intent.action == Intent.ACTION_VIEW && uri != null && isSupportedBook(uri)) {
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
                Text("Your library is empty.", style = MaterialTheme.typography.headlineSmall)
                Text("Import a book to start reading.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(book) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (book.lastPosition > 0) "Continue from page ${book.lastPosition + 1}" else "Not started",
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
    var pages by remember(book.uri) { mutableStateOf<List<String>>(emptyList()) }
    var currentPage by remember(book.uri) { mutableIntStateOf(book.lastPosition.coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(book.uri) {
        pages = runCatching {
            val uri = Uri.parse(book.uri)
            when {
                book.mimeType == "application/pdf" || book.uri.endsWith(".pdf", true) -> loadPdfPagePlaceholders(context, uri)
                book.mimeType == "application/epub+zip" || book.uri.endsWith(".epub", true) -> loadEpubPages(context, uri)
                else -> loadPlainTextPages(context, uri)
            }
        }.getOrElse { listOf("This book could not be read.") }.ifEmpty { listOf("No readable text was found in this book.") }
        currentPage = currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    }

    BackHandler { onBack() }

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
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text("Configure TTS & voice") },
                            onClick = { showMore = false
                                context.startActivity(Intent(context, BooksVoiceSettingsActivity::class.java)) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(enabled = currentPage > 0, onClick = { currentPage = (currentPage - 1).coerceAtLeast(0); onPositionChanged(currentPage) }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous page")
                        }
                        IconButton(enabled = currentPage > 0, onClick = { currentPage = (currentPage - 2).coerceAtLeast(0); onPositionChanged(currentPage) }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Rewind")
                        }
                        IconButton(onClick = { playing = !playing }) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause reading" else "Play reading")
                        }
                        IconButton(enabled = currentPage < pages.lastIndex, onClick = { currentPage = (currentPage + 2).coerceAtMost(pages.lastIndex.coerceAtLeast(0)); onPositionChanged(currentPage) }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Fast forward")
                        }
                        IconButton(enabled = currentPage < pages.lastIndex, onClick = { currentPage = (currentPage + 1).coerceAtMost(pages.lastIndex.coerceAtLeast(0)); onPositionChanged(currentPage) }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next page")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pageInput,
                            onValueChange = { pageInput = it.filter(Char::isDigit) },
                            label = { Text("Page") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(enabled = pageInput.isNotBlank(), onClick = {
                            pageInput.toIntOrNull()?.let { currentPage = (it - 1).coerceIn(0, pages.lastIndex.coerceAtLeast(0)); onPositionChanged(currentPage) }
                        }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Go to page")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("Page ${if (pages.isEmpty()) 0 else currentPage + 1} of ${pages.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(14.dp))
            Text(pages.getOrNull(currentPage).orEmpty().ifBlank { "Loading…" }, modifier = Modifier.fillMaxSize(), style = MaterialTheme.typography.bodyLarge)
        }
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

private fun loadPdfPagePlaceholders(
    context: android.content.Context,
    uri: Uri,
): List<String> {
    val parcel = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
    parcel.use { descriptor ->
        val renderer = android.graphics.pdf.PdfRenderer(descriptor.fileDescriptor)
        return try {
            (0 until renderer.pageCount).map { pageIndex ->
                renderer.openPage(pageIndex).use {
                    "PDF page ${pageIndex + 1}.\n\nPDF text extraction is not available yet. Use the system PDF reader for selectable text."
                }
            }
        } finally {
            renderer.close()
        }
    }
}

private fun loadEpubPages(
    context: android.content.Context,
    uri: Uri,
): List<String> {
    val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
    val bytes = input.use { it.readBytes() }
    val zip = java.util.zip.ZipInputStream(bytes.inputStream())
    val chapters = mutableListOf<String>()
    zip.use { stream ->
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory && (entry.name.endsWith(".xhtml", true) || entry.name.endsWith(".html", true) || entry.name.endsWith(".htm", true))) {
                val html = stream.readBytes().toString(Charsets.UTF_8)
                val text = html
                    .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (text.isNotBlank()) chapters += text
            }
        }
    }
    return chapters.flatMap(::paginateText)
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
