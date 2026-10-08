package app.lawnchair.tools

import android.content.Intent
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec



@Composable
@Suppress("UNUSED_PARAMETER")
private fun ToolsStudioQuickActions(onQr: () -> Unit) {
    OutlinedButton(onClick = onQr, modifier = Modifier.fillMaxWidth()) {
        Text("QR Code")
    }
}
private const val PREFIX = "NXT1"
private const val TAG_BITS = 128
private const val IV_BYTES = 12
private const val SALT_BYTES = 16
private const val KEY_BITS = 256

class ToolsStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent {
            LawnchairTheme {
                ToolsStudioScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun ToolsStudioScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var encryptedWithPassword by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Tools") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, QrGeneratorActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("QR Code Generator")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, PdfToImageActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("PDF to Images")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, ImageCompressorActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Compress Image")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, FileRenamerActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rename Files")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, PasswordGeneratorActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Password Generator")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, TextExtractorActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Image OCR")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, ImageToPdfActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Image to PDF")
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Protect") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Open") })
            }

            if (tab == 0) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; output = ""; error = null },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Text") },
                    placeholder = { Text("Type or paste text") },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        runCatching {
                            output = TextCrypto.encrypt(input, password)
                            encryptedWithPassword = password.isNotEmpty()
                            error = null
                        }.onFailure { error = "Couldn’t encrypt this text." }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Protect Text")
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null; output = "" },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Protected Text") },
                    placeholder = { Text("Paste protected text here") },
                )
                Text("Enter a password only when the protected text uses one.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password (optional)") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        runCatching {
                            output = TextCrypto.decrypt(input, password)
                            error = null
                        }.onFailure { error = "This encrypted text could not be opened. Check the password and try again." }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Text")
                }
            }

            error?.let { Text(it) }

            if (output.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = output,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (tab == 0) "Protected Text" else "Opened Text") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(output))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, output)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                            context.startActivity(Intent.createChooser(share, "Share text"))
                        }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            input = ""
                            password = ""
                            output = ""
                            error = null
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear")
                        }
                    }
                    if (tab == 0) {
                        Text(if (encryptedWithPassword) "Password protected" else "Saved without a password")
                    }
                }
            }
        }
    }
}

private object TextCrypto {
    fun encrypt(text: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val auth = if (password.isEmpty()) maclessFingerprint(salt, iv) else ByteArray(0)
        return listOf(
            PREFIX,
            if (password.isEmpty()) "0" else "1",
            b64(salt),
            b64(iv),
            b64(ciphertext),
            b64(auth),
        ).joinToString(".")
    }

    fun decrypt(encoded: String, password: String): String {
        val p = encoded.split(".")
        require(p.size == 6 && p[0] == PREFIX) { "Unsupported encrypted text" }
        val hasPassword = p[1] == "1"
        require(!hasPassword || password.isNotEmpty()) { "Password required" }
        if (!hasPassword) require(password.isEmpty()) { "No password is required" }
        val salt = db64(p[2])
        val iv = db64(p[3])
        val ciphertext = db64(p[4])
        val expectedAuth = db64(p[5])
        if (!hasPassword && !MessageDigest.isEqual(expectedAuth, maclessFingerprint(salt, iv))) {
            throw IllegalArgumentException("Invalid text")
        }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        var result = salt
        val secret = (if (password.isEmpty()) "NexusToolsStudio".toByteArray() else password.toByteArray(Charsets.UTF_8))
        repeat(15000) {
            result = hmac(secret, result)
        }
        return result.copyOf(KEY_BITS / 8)
    }

    private fun maclessFingerprint(salt: ByteArray, iv: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + iv + "NexusToolsStudio".toByteArray()).copyOf(16)

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }.doFinal(data)

    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun db64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
}
