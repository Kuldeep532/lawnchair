package app.lawnchair.books

import android.content.Intent
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer

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
        store = BooksStore.getInstance(this)

        val externalUri = intent?.data
        if (externalUri != null && intent?.action == Intent.ACTION_VIEW && isSupportedBook(externalUri)) {
            importAndOpen(externalUri)
        } else {
            showLibrary()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
                EdgeToEdge()
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
                EdgeToEdge()
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
                title = { Text("Your books") },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add a book")
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
                Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
                Text("Add a PDF, EPUB, text or Markdown book to start reading.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable { onOpen(book) }.padding(18.dp),
                            ) {
                                Text(book.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (book.lastPosition > 0) "Continue from page ${book.lastPosition + 1}" else "Ready to read",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            var showBookMenu by remember(book.id) { mutableStateOf(false) }
                            IconButton(onClick = { showBookMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${book.title}")
                            }
                            DropdownMenu(
                                expanded = showBookMenu,
                                onDismissRequest = { showBookMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from library") },
                                    onClick = { showBookMenu = false; onDelete(book) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                )
                            }
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
    val context = LocalContext.current
    var pages by remember(book.uri) { mutableStateOf<List<String>>(emptyList()) }
    var currentPage by remember(book.uri) { mutableIntStateOf(book.lastPosition.coerceAtLeast(0)) }
    var pageInput by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var showPageDialog by remember { mutableStateOf(false) }
    var readerFontSize by remember { mutableStateOf(20f) }
    var speech by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrMessage by remember { mutableStateOf<String?>(null) }
    val pageScrollState = remember { androidx.compose.foundation.ScrollState(0) }

    LaunchedEffect(currentPage) {
        pageScrollState.scrollTo(0)
    }

    fun stopReading() {
        runCatching { speech?.stop() }
        speaking = false
    }

    fun goToPage(target: Int) {
        stopReading()
        currentPage = target.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        pageInput = ""
        onPositionChanged(currentPage)
    }

    DisposableEffect(Unit) {
        lateinit var engine: android.speech.tts.TextToSpeech
        engine = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val locale = Locale.getDefault()
                if (engineSafeAvailable(engine, locale)) engine.language = locale
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("book-page") == true) speaking = false
            }
            override fun onError(utteranceId: String?) {
                speaking = false
            }
        })
        speech = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            speech = null
        }
    }

    LaunchedEffect(book.uri) {
        val isPdf = book.mimeType == "application/pdf" || book.uri.endsWith(".pdf", true)
        if (isPdf) {
            ocrBusy = true
            ocrMessage = "Preparing readable text from this PDF…"
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(book.uri)
                when {
                    isPdf -> loadPdfPagePlaceholders(context, uri)
                    book.mimeType == "application/epub+zip" || book.uri.endsWith(".epub", true) -> loadEpubPages(context, uri)
                    else -> loadPlainTextPages(context, uri)
                }
            }
        }
        pages = result.getOrElse { listOf("This book could not be opened. Check that the file is still available.") }
            .ifEmpty { listOf("No readable text was found in this book.") }
        currentPage = currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        if (isPdf) {
            ocrMessage = if (result.isSuccess) "Text is ready for reading." else "Text could not be extracted from this PDF."
            ocrBusy = false
        }
    }

    BackHandler { stopReading(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { stopReading(); onBack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to your books")
                    }
                },
                title = {
                    Column {
                        Text(book.title, maxLines = 1)
                        Text("Reading", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Reading options")
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(
                            text = { Text("Smaller text") },
                            onClick = { readerFontSize = (readerFontSize - 1f).coerceAtLeast(16f); showMore = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Larger text") },
                            onClick = { readerFontSize = (readerFontSize + 1f).coerceAtMost(30f); showMore = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Reader voice settings") },
                            onClick = {
                                showMore = false
                                context.startActivity(Intent(context, BooksVoiceSettingsActivity::class.java))
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            enabled = currentPage > 0,
                            onClick = { goToPage(currentPage - 1) },
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous page")
                        }
                        IconButton(
                            onClick = {
                                val text = pages.getOrNull(currentPage).orEmpty()
                                val engine = speech
                                if (engine == null || text.isBlank()) return@IconButton
                                if (speaking) {
                                    engine.stop()
                                    speaking = false
                                } else {
                                    speaking = true
                                    engine.speak(
                                        text,
                                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "book-page-$currentPage",
                                    )
                                }
                            },
                        ) {
                            Icon(
                                if (speaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (speaking) "Pause reading aloud" else "Read this page aloud",
                            )
                        }
                        IconButton(
                            enabled = currentPage < pages.lastIndex,
                            onClick = { goToPage(currentPage + 1) },
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next page")
                        }
                    }
                    Slider(
                        value = currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0)).toFloat(),
                        onValueChange = { currentPage = it.toInt().coerceIn(0, pages.lastIndex.coerceAtLeast(0)) },
                        onValueChangeFinished = { onPositionChanged(currentPage) },
                        valueRange = 0f..pages.lastIndex.coerceAtLeast(0).toFloat(),
                        enabled = pages.size > 1,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Page ${if (pages.isEmpty()) 0 else currentPage + 1} of ${pages.size}", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { showPageDialog = true }) { Text("Go to page") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ocrBusy || ocrMessage != null) {
                Text(
                    ocrMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(pageScrollState),
                    ) {
                        Text(
                            pages.getOrNull(currentPage).orEmpty().ifBlank { "Opening your book…" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = readerFontSize.sp,
                                lineHeight = (readerFontSize * 1.6f).sp,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showPageDialog) {
        AlertDialog(
            onDismissRequest = { showPageDialog = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter(Char::isDigit) },
                    label = { Text("Page number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pageInput.toIntOrNull()?.let { goToPage(it - 1) }
                        showPageDialog = false
                    },
                    enabled = pageInput.toIntOrNull()?.let { it in 1..pages.size } == true,
                ) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showPageDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun engineSafeAvailable(
    engine: android.speech.tts.TextToSpeech,
    locale: Locale,
): Boolean = engine.isLanguageAvailable(locale) >= android.speech.tts.TextToSpeech.LANG_AVAILABLE


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
        val renderer = android.graphics.pdf.PdfRenderer(descriptor)
        return try {
            (0 until renderer.pageCount).map { pageIndex ->
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        (page.width * 1.5f).toInt().coerceAtLeast(1),
                        (page.height * 1.5f).toInt().coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    try {
                        page.render(
                            bitmap,
                            null,
                            null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
                        )
                        try {
                            val text = com.google.android.gms.tasks.Tasks.await(
                                recognizer.process(
                                    com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0),
                                ),
                            ).text.trim()
                            if (text.isBlank()) {
                                "PDF page ${pageIndex + 1}. No readable text was found."
                            } else {
                                text
                            }
                        } finally {
                            recognizer.close()
                        }
                    } finally {
                        bitmap.recycle()
                    }
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
