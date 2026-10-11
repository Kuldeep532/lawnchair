package app.lawnchair.tools

import android.content.Intent
import android.net.Uri
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
            0 -> {
                val candidate = text.trim()
                val normalized = if (candidate.isNotEmpty() &&
                    !candidate.startsWith("https://", true) &&
                    !candidate.startsWith("http://", true)
                ) "https://$candidate" else candidate
                runCatching {
                    val uri = URI(normalized)
                    val host = uri.host ?: throw IllegalArgumentException()
                    val domain = host.lowercase(Locale.ROOT).removePrefix("www.")
                    val clean = URI(
                        uri.scheme?.lowercase(Locale.ROOT) ?: "https",
                        uri.userInfo,
                        host.lowercase(Locale.ROOT),
                        uri.port,
                        uri.path,
                        uri.query,
                        null,
                    ).toASCIIString()
                    "Domain: $domain\nClean link: $clean"
                }.getOrElse {
                    message = "Enter a valid web address, such as example.com."
                    ""
                }
            }
            1 -> {
                val cleaned = text
                    .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex(" *\\n *"), "\n")
                    .trim()
                "Clean text:\n$cleaned\n\nWords: ${if (cleaned.isBlank()) 0 else cleaned.trim().split(Regex("\\s+")).size}\nCharacters: ${cleaned.length}\nCharacters (excluding spaces): ${cleaned.count { !it.isWhitespace() }}"
            }
            else -> {
                val normalized = text.trim().replace(Regex("\\s+"), "")
                if (normalized.isBlank()) {
                    message = "Paste a link first."
                    ""
                } else {
                    val uri = runCatching {
                        URI(if (normalized.startsWith("http", true)) normalized else "https://$normalized")
                    }.getOrNull()
                    val host = uri?.host
                    if (host == null) {
                        message = "That link doesn’t look valid. Check it and try again."
                        ""
                    } else {
                        "Website: ${host.removePrefix("www.")}\nProtocol: ${uri.scheme}\nPath: ${uri.path.ifBlank { "/" }}"
                    }
                }
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
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Handy everyday tools in one place. These tools work on your device and do not show ads.")
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; result = ""; message = "" }, text = { Text("Clean Link") })
                Tab(selected = tab == 1, onClick = { tab = 1; result = ""; message = "" }, text = { Text("Clean Text") })
                Tab(selected = tab == 2, onClick = { tab = 2; result = ""; message = "" }, text = { Text("Link Info") })
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; result = ""; message = "" },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
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
                onClick = {
                    text = ""
                    result = ""
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotEmpty() || result.isNotEmpty(),
            ) { Text("Clear") }
        }
    }
}
