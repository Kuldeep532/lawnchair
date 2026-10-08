package app.lawnchair.nexuslauncher.tools

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
import androidx.activity.BackHandler
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
    }

    fun payload(): String = when (mode) {
        QrMode.TEXT -> input
        QrMode.URL -> input
        QrMode.WIFI -> "WIFI:T:$wifiSecurity;S:${wifiEscape(input)};P:${wifiEscape(wifiPassword)};;"
        QrMode.UPI -> "upi://pay?pa=${Uri.encode(upiId.trim())}&pn=${Uri.encode(upiName.trim())}&am=${amount.trim()}&cu=INR&tn=${Uri.encode(notes.trim())}"
    }

    fun generate() {
        error = null
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
            result != null -> ResultScreen(result!!, { saveQr(context, result!!) }, { downloadQr(context, result!!) }, { shareQr(context, result!!) }, ::reset)
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
                        field("Security: WPA, WEP or nopass", wifiSecurity) { wifiSecurity = it }
                        field("Wi-Fi password", wifiPassword) { wifiPassword = it }
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
    save: () -> Unit,
    download: () -> Unit,
    share: () -> Unit,
    regenerate: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "Generated QR code", modifier = Modifier.size(320.dp))
        Text("QR code generated successfully.")
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

private fun saveQr(context: Context, bitmap: Bitmap) {
    storeQr(context, bitmap, "Pictures/Nexus QR")
}

private fun downloadQr(context: Context, bitmap: Bitmap) {
    storeQr(context, bitmap, "Download/Nexus QR")
}

private fun storeQr(context: Context, bitmap: Bitmap, path: String) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nexus_QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, path)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }.onFailure { resolver.delete(uri, null, null) }
}

private fun shareQr(context: Context, bitmap: Bitmap) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nexus_QR_Share.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nexus QR")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share QR code"))
    }.onFailure { resolver.delete(uri, null, null) }
}

private fun wifiEscape(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
