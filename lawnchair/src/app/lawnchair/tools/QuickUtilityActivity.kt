package app.lawnchair.tools

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import java.net.URI
import java.util.Locale

class QuickUtilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { QuickUtilityScreen(::finish) } }
    }
}

@Composable
private fun QuickUtilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun transform() {
        message = ""
        result = when (tab) {
            0 -> runCatching {
                val source = normalizeWebAddress(text)
                val uri = URI(source)
                val host = uri.host ?: throw IllegalArgumentException()
                val cleaned = URI(
                    uri.scheme.lowercase(Locale.ROOT),
                    null, // Never preserve user-info credentials in a shared URL.
                    host.lowercase(Locale.ROOT),
                    uri.port,
                    uri.path.ifBlank { "/" },
                    uri.query,
                    null, // Drop the fragment; preserve the query exactly.
                ).toASCIIString()
                "Domain: ${host.lowercase(Locale.ROOT).removePrefix("www.")}\nClean link: $cleaned"
            }.getOrElse {
                message = "Enter a valid web address, such as example.com."
                ""
            }
            1 -> {
                val cleaned = text
                    .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .trim()
                val wordCount = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
                    .findAll(cleaned).count()
                val codePoints = cleaned.codePointCount(0, cleaned.length)
                val noWhitespaceCodePoints = cleaned.codePoints()
                    .filter { cp -> !Character.isWhitespace(cp) }
                    .count()
                "Clean text:\n$cleaned\n\nWords: $wordCount\nCharacters: $codePoints\nCharacters (excluding spaces): $noWhitespaceCodePoints"
            }
            else -> runCatching {
                val uri = URI(normalizeWebAddress(text))
                val host = uri.host ?: throw IllegalArgumentException()
                val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
                buildString {
                    append("Website: ${host.lowercase(Locale.ROOT).removePrefix("www.")}")
                    append("\nProtocol: ${uri.scheme.lowercase(Locale.ROOT)}")
                    append("\nPath: $path")
                    if (!uri.rawQuery.isNullOrBlank()) append("\nQuery parameters: ${uri.rawQuery.split('&').size}")
                    if (!uri.rawFragment.isNullOrBlank()) append("\nFragment: present")
                }
            }.getOrElse {
                message = "That link doesn’t look valid. Check it and try again."
                ""
            }
        }
    }

    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Quick Utility Hub") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Tools Studio")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Useful everyday helpers in one place. Processing happens on your device; no ads or website upload is needed.")
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; result = ""; message = "" }, text = { Text("Clean Link") })
                Tab(selected = tab == 1, onClick = { tab = 1; result = ""; message = "" }, text = { Text("Clean Text") })
                Tab(selected = tab == 2, onClick = { tab = 2; result = ""; message = "" }, text = { Text("Link Info") })
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; result = ""; message = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (tab == 1) "Text to clean" else "Website address or link") },
                placeholder = { Text(if (tab == 1) "Paste text here" else "example.com or paste a link") },
                minLines = if (tab == 1) 5 else 2,
            )
            Button(onClick = ::transform, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                Text(when (tab) { 0 -> "Clean Link"; 1 -> "Clean Text"; else -> "Get Link Info" })
            }
            if (message.isNotBlank()) Text(message)
            if (result.isNotBlank()) {
                OutlinedTextField(
                    value = result,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Result") },
                    minLines = 2,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(result)); message = "Result copied." },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, result)
                            context.startActivity(Intent.createChooser(send, "Share result"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null)
                        Text("Share")
                    }
                }
            }
            OutlinedButton(
                onClick = { text = ""; result = ""; message = "" },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotEmpty() || result.isNotEmpty(),
            ) { Text("Clear") }
        }
    }
}

private fun normalizeWebAddress(raw: String): String {
    val candidate = raw.trim()
    require(candidate.isNotBlank()) { "Address is empty" }
    require(!candidate.any { it.isWhitespace() }) { "Address contains spaces" }
    val normalized = if (
        candidate.startsWith("https://", ignoreCase = true) ||
        candidate.startsWith("http://", ignoreCase = true)
    ) candidate else "https://$candidate"
    val uri = URI(normalized)
    require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
    require(!uri.host.isNullOrBlank())
    return uri.toASCIIString()
}
