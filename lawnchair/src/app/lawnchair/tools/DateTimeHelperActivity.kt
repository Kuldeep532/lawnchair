package app.lawnchair.tools

import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import java.util.TimeZone

class DateTimeHelperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { DateTimeHelperScreen(::finish) } }
    }
}

@Composable
private fun DateTimeHelperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun convert() {
        message = ""
        result = runCatching {
            val value = input.trim()
            require(value.isNotEmpty()) { "A date or timestamp is required." }
            val numeric = value.toLongOrNull()
            val date = if (numeric != null) {
                val milliseconds = if (value.length <= 10) Math.multiplyExact(numeric, 1000L) else numeric
                Date(milliseconds)
            } else {
                val patterns = listOf(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mmXXX",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd HH:mm",
                    "yyyy-MM-dd",
                    "dd-MM-yyyy HH:mm",
                    "dd-MM-yyyy",
                )
                var parsed: Date? = null
                for (pattern in patterns) {
                    val parser = SimpleDateFormat(pattern, Locale.ROOT).apply {
                        isLenient = false
                        timeZone = TimeZone.getDefault()
                    }
                    val attempt = runCatching { parser.parse(value) }.getOrNull()
                    if (attempt != null) {
                        parsed = attempt
                        break
                    }
                }
                requireNotNull(parsed) { "Unsupported date format." }
            }
            val local = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(date)
            val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)
            val epochSeconds = Math.floorDiv(date.time, 1000L)
            val relative = DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            "Local time: $local\nUTC: $utc\nUnix timestamp (seconds): $epochSeconds\nUnix timestamp (milliseconds): ${date.time}\nRelative time: $relative"
        }.getOrElse {
            message = "Enter a supported date/time or Unix timestamp. Examples: 2026-10-11 16:30, 11-10-2026, or a Unix timestamp."
            ""
        }
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Date & Time Converter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Tools Studio")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Convert dates and Unix timestamps locally. Nothing is uploaded, and there are no ads in this tool.")
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; result = ""; message = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date, time, or Unix timestamp") },
                placeholder = { Text("2026-10-11 16:30 or 1791716400") },
                minLines = 2,
            )
            Button(
                onClick = ::convert,
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Convert") }
            if (message.isNotBlank()) Text(message)
            if (result.isNotBlank()) {
                OutlinedTextField(
                    value = result,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Conversion result") },
                    minLines = 5,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(result))
                            message = "Result copied."
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, result)
                            context.startActivity(Intent.createChooser(share, "Share conversion"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null)
                        Text("Share")
                    }
                }
            }
            OutlinedButton(
                onClick = { input = ""; result = ""; message = "" },
                enabled = input.isNotEmpty() || result.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Text("Clear")
            }
        }
    }
}
