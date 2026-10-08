package app.lawnchair.secureworld

import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import java.util.concurrent.Executor

class SecureWorldActivity : ComponentActivity() {

    private lateinit var promptExecutor: Executor
    private lateinit var store: SecureWorldStore

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            val suggestedName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: "Document"
            showDocumentDialog(uri.toString(), suggestedName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        store = SecureWorldStore.getInstance(this)
        promptExecutor = ContextCompat.getMainExecutor(this)
        authenticateAndShow()
    }

    private fun authenticateAndShow() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            finish()
            return
        }

        BiometricPrompt(
            this,
            promptExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    showSecureVault()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure Vault")
                .setSubtitle("Unlock your private space")
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false)
                .build(),
        )
    }

    private fun showDocumentDialog(
        uri: String,
        suggestedName: String,
        extractedText: String = "",
    ) {
        setContent {
            LawnchairTheme {
                DocumentDialog(
                    suggestedName = suggestedName,
                    suggestedNumber = suggestDocumentNumber("Other", extractedText),
                    onDismiss = { showSecureVault() },
                    onSave = { category, number, name, details ->
                        val finalNumber = number.ifBlank { suggestDocumentNumber(category, extractedText) }
                        store.saveDocument(category, finalNumber, name, details, uri)
                        showSecureVault()
                    },
                )
            }
        }
    }

    private fun showSecureVault() {
        setContent {
            LawnchairTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    var showNoteDialog by remember { mutableStateOf(false) }
                    var showAddDocument by remember { mutableStateOf(false) }
                    var showPasswordDialog by remember { mutableStateOf(false) }
                    var showCloseDialog by remember { mutableStateOf(false) }

                    BackHandler { showCloseDialog = true }

                    val tabs = listOf("Notes", "Documents", "Passwords")

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Secure Vault", style = MaterialTheme.typography.titleLarge)
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Secure Vault protected area",
                            )
                        }

                        TabRow(selectedTabIndex = selectedTab) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title) },
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> NotesTab(store.notes(), { showNoteDialog = true }, store::deleteNote)
                            1 -> DocumentsTab(
                                items = store.documents(),
                                onAdd = { showAddDocument = true },
                                onDelete = store::deleteDocument,
                                onOpen = ::openDocument,
                            )
                            else -> PasswordsTab(store.passwords(), { showPasswordDialog = true }, store::deletePassword)
                        }
                    }

                    if (showNoteDialog) {
                        NoteDialog(
                            onDismiss = { showNoteDialog = false },
                            onSave = { title, body ->
                                store.saveNote(title, body)
                                showNoteDialog = false
                            },
                        )
                    }

                    if (showAddDocument) {
                        AddDocumentChoiceDialog(
                            onDismiss = { showAddDocument = false },
                            onUpload = {
                                showAddDocument = false
                                documentPicker.launch(arrayOf("*/*"))
                            },
                            onDetailsOnly = {
                                showAddDocument = false
                                showDocumentDialog("", "")
                            },
                        )
                    }

                    if (showCloseDialog) {
                        CloseSecureVaultDialog(
                            onDismiss = { showCloseDialog = false },
                            onConfirm = {
                                showCloseDialog = false
                                finish()
                            },
                        )
                    }

                    if (showPasswordDialog) {
                        PasswordDialog(
                            onDismiss = { showPasswordDialog = false },
                            onSave = { title, username, password ->
                                store.savePassword(title, username, password)
                                showPasswordDialog = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun openDocument(uri: String) {
        if (uri.isBlank()) return
        val parsed = android.net.Uri.parse(uri)
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(parsed, contentResolver.getType(parsed) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
}

@androidx.compose.runtime.Composable
private fun NotesTab(
    items: List<SecureNote>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ActionRow("Notes", onAdd)
        LazyColumn {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DocumentsTab(
    items: List<SecureDocument>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ActionRow("Documents", onAdd)
        LazyColumn {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(enabled = item.uri.isNotBlank()) { onOpen(item.uri) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(item.name.ifBlank { "Document" }, style = MaterialTheme.typography.titleMedium)
                            Text(item.category, style = MaterialTheme.typography.labelMedium)
                            if (item.number.isNotBlank()) {
                                Text("Number: ${item.number}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (item.details.isNotBlank()) {
                                Text(item.details, style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.uri.isBlank()) {
                                Text("Details only", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete document")
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PasswordsTab(
    items: List<SecurePassword>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ActionRow("Passwords", onAdd)
        LazyColumn {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.username, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete password")
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ActionRow(label: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = "Add $label")
        }
    }
}

@androidx.compose.runtime.Composable
private fun AddDocumentChoiceDialog(
    onDismiss: () -> Unit,
    onUpload: () -> Unit,
    onDetailsOnly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                    Text("Upload a file")
                }
                Button(onClick = onDetailsOnly, modifier = Modifier.fillMaxWidth()) {
                    Text("Add details without a file")
                }
            }
        },
        confirmButton = {},
    )
}

@androidx.compose.runtime.Composable
private fun DocumentDialog(
    suggestedName: String,
    suggestedNumber: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val categories = listOf(
        "Aadhaar Card",
        "PAN Card",
        "Driving Licence",
        "Certificate",
        "Passport",
        "Voter ID",
        "Insurance",
        "Other",
    )
    var category by remember { mutableStateOf(categories.first()) }
    var number by remember { mutableStateOf(suggestedNumber) }
    var name by remember { mutableStateOf(suggestedName) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                categories.forEach { option ->
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { category = option }
                            .padding(vertical = 6.dp),
                        style = if (category == option) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    )
                }
                OutlinedTextField(number, { number = it }, label = { Text("Document number") })
                OutlinedTextField(name, { name = it }, label = { Text("Document name") })
                OutlinedTextField(details, { details = it }, label = { Text("Details") })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(category, number, name, details) },
            ) {
                Text("Save")
            }
        },
    )
}

@androidx.compose.runtime.Composable
private fun CloseSecureVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Do you want to close Secure Vault?") },
        text = { Text("Secure Vault will lock when it closes. You will need fingerprint unlock to open it again.") },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Close") }
        },
    )
}

@androidx.compose.runtime.Composable
private fun NoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(body, { body = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { onSave(title, body) }) {
                Text("Save")
            }
        },
    )
}

@androidx.compose.runtime.Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name") })
                OutlinedTextField(username, { username = it }, label = { Text("Username") })
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = { onSave(title, username, password) },
            ) {
                Text("Save")
            }
        },
    )
}


/**
 * Normalizes text extracted from a document image before placing it in the document number field.
 * This intentionally stays lightweight; the actual OCR engine is loaded on demand.
 */
private fun suggestDocumentNumber(category: String, extractedText: String): String {
    val text = extractedText.uppercase()
    val candidates = when (category) {
        "PAN Card" -> Regex("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b").findAll(text).map { it.value }
        "Aadhaar Card" -> Regex("\\b[0-9]{4}[ -]?[0-9]{4}[ -]?[0-9]{4}\\b").findAll(text).map { it.value }
        "Driving Licence" -> Regex("\\b[A-Z]{2}[ -]?[0-9]{2}[A-Z0-9 -]{8,16}\\b").findAll(text).map { it.value.trim() }
        "Voter ID" -> Regex("\\b[A-Z]{3}[0-9]{7}\\b").findAll(text).map { it.value }
        "Passport" -> Regex("\\b[A-Z][0-9]{7}\\b").findAll(text).map { it.value }
        else -> emptySequence()
    }
    return candidates.firstOrNull().orEmpty()
}
