package app.lawnchair.secureworld

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            store.saveDocument(
                name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document",
                uri = uri.toString(),
            )
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        store = SecureWorldStore.getInstance(this)
        promptExecutor = ContextCompat.getMainExecutor(this)
        authenticateAndShow()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && ::promptExecutor.isInitialized && !isChangingConfigurations) {
            // The initial unlock happens in onCreate. Re-authentication can be enabled
            // for background timeout in a later stage without changing the workspace.
        }
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
                    showSecureWorld()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(com.android.launcher3.R.string.secure_world_unlock_title))
                .setSubtitle(getString(com.android.launcher3.R.string.secure_world_unlock_subtitle))
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false)
                .build(),
        )
    }

    private fun showSecureWorld() {
        setContent {
            LawnchairTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    var showNoteDialog by remember { mutableStateOf(false) }
                    var showPasswordDialog by remember { mutableStateOf(false) }

                    val tabs = listOf(
                        getString(com.android.launcher3.R.string.secure_world_notes),
                        getString(com.android.launcher3.R.string.secure_world_documents),
                        getString(com.android.launcher3.R.string.secure_world_passwords),
                    )

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = getString(com.android.launcher3.R.string.secure_world_label),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Secure World locked area",
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
                            0 -> NotesTab(
                                items = store.notes(),
                                onAdd = { showNoteDialog = true },
                                onDelete = store::deleteNote,
                            )

                            1 -> DocumentsTab(
                                items = store.documents(),
                                onAdd = { documentPicker.launch(arrayOf("*/*")) },
                                onDelete = store::deleteDocument,
                            )

                            else -> PasswordsTab(
                                items = store.passwords(),
                                onAdd = { showPasswordDialog = true },
                                onDelete = store::deletePassword,
                            )
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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ActionRow("Documents", onAdd)
        LazyColumn {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete document")
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
