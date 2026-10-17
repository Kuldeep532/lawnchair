package app.lawnchair.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

private enum class QrMode(val label: String) { TEXT("Text"), URL("URL"), WIFI("Wi-Fi"), UPI("UPI") }

class QrGeneratorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { QrScreen(::finish) } }
    }
}

@Composable
private fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(QrMode.TEXT) }
    var input by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    var upiName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    fun reset() {
        input = ""
        wifiPassword = ""
        wifiSecurity = "WPA"
        upiName = ""
        upiId = ""
        amount = ""
        notes = ""
        result = null
        error = null
        resultMessage = null
    }

    fun payload(): String = when (mode) {
        QrMode.TEXT -> input.trim()
        QrMode.URL -> normalizeQrUrl(input)
        QrMode.WIFI -> {
            val ssid = input.trim()
            require(ssid.isNotEmpty()) { "Enter the Wi-Fi network name (SSID)." }
            require(wifiSecurity in setOf("WPA", "WEP", "nopass")) { "Choose WPA, WEP or no password." }
            if (wifiSecurity != "nopass") {
                require(wifiPassword.isNotEmpty()) { "Enter the Wi-Fi password or choose no password." }
            }
            val passwordPart = if (wifiSecurity == "nopass") "" else "P:${wifiEscape(wifiPassword)};"
            "WIFI:T:$wifiSecurity;S:${wifiEscape(ssid)};$passwordPart;"
        }
        QrMode.UPI -> {
            val payeeId = upiId.trim()
            require(Regex("^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$").matches(payeeId)) {
                "Enter a valid UPI ID, such as name@bank."
            }
            val normalizedAmount = amount.trim()
            if (normalizedAmount.isNotEmpty()) {
                require(Regex("^\\d+(\\.\\d{1,2})?$").matches(normalizedAmount)) {
                    "Enter a valid amount with up to two decimal places."
                }
                require((normalizedAmount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) > java.math.BigDecimal.ZERO) {
                    "Amount must be greater than zero."
                }
            }
            Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", payeeId)
                .appendQueryParameter("cu", "INR")
                .apply {
                    if (upiName.isNotBlank()) appendQueryParameter("pn", upiName.trim())
                    if (normalizedAmount.isNotEmpty()) appendQueryParameter("am", normalizedAmount)
                    if (notes.isNotBlank()) appendQueryParameter("tn", notes.trim())
                }
                .build()
                .toString()
        }
    }

    fun generate() {
        error = null
        resultMessage = null
        loading = true
        result = runCatching {
            val text = payload().trim()
            require(text.isNotEmpty()) { "Enter content first." }
            val matrix = MultiFormatWriter().encode(
                text, BarcodeFormat.QR_CODE, 900, 900,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2),
            )
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }.onFailure { error = it.message ?: "Unable to generate QR code." }.getOrNull()
        loading = false
    }

    BackHandler { if (result != null) reset() else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (result != null) reset() else onBack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Tools Studio")
                    }
                },
                title = { Text("QR Code Generator") },
            )
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Generating QR code…")
                }
            }
            result != null -> ResultScreen(
                bitmap = result!!,
                contentPadding = padding,
                save = {
                    resultMessage = if (saveQr(context, result!!)) {
                        "QR code saved to Pictures/Nexus QR."
                    } else {
                        "Could not save the QR code. Check available storage and try again."
                    }
                },
                download = {
                    resultMessage = if (downloadQr(context, result!!)) {
                        "QR code saved to Downloads/Nexus QR."
                    } else {
                        "Could not download the QR code. Check available storage and try again."
                    }
                },
                share = {
                    resultMessage = if (shareQr(context, result!!)) {
                        "Share options opened."
                    } else {
                        "Could not open sharing. Try saving the QR code first."
                    }
                },
                regenerate = ::reset,
                message = resultMessage,
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TabRow(mode.ordinal) {
                    QrMode.entries.forEach { item -> Tab(mode == item, { mode = item; error = null }) { Text(item.label) } }
                }
                when (mode) {
                    QrMode.TEXT -> field("Text", input) { input = it }
                    QrMode.URL -> field("URL", input) { input = it }
                    QrMode.WIFI -> {
                        field("Wi-Fi SSID", input) { input = it }
                        Text("Security")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WPA", "WEP", "nopass").forEach { security ->
                                FilterChip(
                                    selected = wifiSecurity == security,
                                    onClick = { wifiSecurity = security },
                                    label = { Text(security) },
                                )
                            }
                        }
                        if (wifiSecurity != "nopass") {
                            field("Wi-Fi password", wifiPassword) { wifiPassword = it }
                        }
                    }
                    QrMode.UPI -> {
                        field("Payee name", upiName) { upiName = it }
                        field("UPI ID", upiId) { upiId = it }
                        field("Amount", amount) { amount = it }
                        field("Payment note", notes) { notes = it }
                        Text("UPI supports name, ID, amount and note. Category selection will remain metadata-only.")
                    }
                }
                error?.let { Text(it) }
                Button(::generate, Modifier.fillMaxWidth()) { Text("Generate QR code") }
            }
        }
    }
}

@Composable
private fun field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

@Composable
private fun ResultScreen(
    bitmap: Bitmap,
    contentPadding: PaddingValues,
    save: () -> Unit,
    download: () -> Unit,
    share: () -> Unit,
    regenerate: () -> Unit,
    message: String?,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "Generated QR code", modifier = Modifier.size(320.dp))
        Text("QR code generated successfully.")
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(download, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Download")
            }
            OutlinedButton(save, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }
        OutlinedButton(share, Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Share")
        }
        Button(regenerate, Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Regenerate")
        }
    }
}

private fun saveQr(context: Context, bitmap: Bitmap): Boolean =
    storeQr(context, bitmap, "Pictures/Nexus QR")

private fun downloadQr(context: Context, bitmap: Bitmap): Boolean =
    storeQr(context, bitmap, "Download/Nexus QR")

private fun storeQr(context: Context, bitmap: Bitmap, path: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nexus_QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, path)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        val stream = resolver.openOutputStream(uri) ?: error("Could not open image output.")
        stream.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "QR image encoding failed." } }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) > 0) { "Could not finish saving the QR image." }
    }.onFailure {
        resolver.delete(uri, null, null)
    }.isSuccess
}

private fun shareQr(context: Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nexus_QR_Share_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nexus QR")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        val stream = resolver.openOutputStream(uri) ?: error("Could not open image output.")
        stream.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "QR image encoding failed." } }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) > 0) { "Could not finish saving the QR image." }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share QR code"))
    }.onFailure {
        resolver.delete(uri, null, null)
    }.isSuccess
}

private fun normalizeQrUrl(value: String): String {
    val candidate = value.trim()
    require(candidate.isNotEmpty()) { "Enter a website URL." }
    require(!candidate.any(Char::isWhitespace)) { "URL cannot contain spaces." }
    val normalized = if (
        candidate.startsWith("https://", ignoreCase = true) ||
        candidate.startsWith("http://", ignoreCase = true)
    ) candidate else "https://$candidate"
    val uri = runCatching { java.net.URI(normalized) }
        .getOrElse { throw IllegalArgumentException("Enter a valid website URL.") }
    require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
        "Only HTTP and HTTPS website URLs are supported."
    }
    require(!uri.host.isNullOrBlank()) { "Enter a valid website address." }
    return uri.toASCIIString()
}

private fun wifiEscape(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
