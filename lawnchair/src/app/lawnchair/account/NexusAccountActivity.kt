package app.lawnchair.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.assistant.NexusCloudAssistant
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional online Nexus account management. The launcher itself never requires authentication.
 */
class NexusAccountActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent {
            LawnchairTheme {
                val context = LocalContext.current
                var account by remember { mutableStateOf(NexusAccountStore.account(context)) }
                var name by remember { mutableStateOf("") }
                var email by remember { mutableStateOf(account?.email.orEmpty()) }
                var password by remember { mutableStateOf("") }
                var createAccount by remember { mutableStateOf(false) }
                var accepted by remember { mutableStateOf(NexusAccountStore.hasAcceptedLegal(context)) }
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }
                var showPrivacy by remember { mutableStateOf(false) }
                var showTerms by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Nexus Account") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = account?.name?.takeIf { it.isNotBlank() }?.let { "Welcome, $it" }
                                ?: "Sign in with your Nexus Account",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Nexus Launcher works without an account. Sign in only for account-linked online services such as cloud AI.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (account != null) {
                            Text("Name: ${account?.name.orEmpty()}")
                            Text("Email: ${account?.email.orEmpty()}")
                            HorizontalDivider()
                            Text("Privacy Policy and Terms", style = MaterialTheme.typography.titleMedium)
                            Text("Read these documents at any time. Acceptance is required only to use Nexus Account services; it is not needed for ordinary launcher use.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showPrivacy = true }) { Text("Read Privacy Policy") }
                                TextButton(onClick = { showTerms = true }) { Text("Read Terms") }
                            }
                            Button(
                                onClick = {
                                    NexusAccountStore.signOut(context)
                                    NexusCloudAssistant.clearSession(context)
                                    account = null
                                    name = ""
                                    password = ""
                                    message = "Signed out. Launcher features remain available."
                                    error = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Sign out") }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { createAccount = false; message = "" }) {
                                    Text(if (!createAccount) "✓ Sign in" else "Sign in")
                                }
                                TextButton(onClick = { createAccount = true; message = "" }) {
                                    Text(if (createAccount) "✓ Create account" else "Create account")
                                }
                            }
                            if (createAccount) {
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it },
                                    label = { Text("Name") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            OutlinedTextField(
                                value = email, onValueChange = { email = it },
                                label = { Text("Email") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = password, onValueChange = { password = it },
                                label = { Text("Password") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = accepted,
                                    onCheckedChange = {
                                        accepted = it
                                        NexusAccountStore.setLegalAccepted(context, it)
                                    },
                                )
                                Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                                    Text("I accept the Privacy Policy and Terms & Conditions.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { showPrivacy = true }) { Text("Read Privacy Policy") }
                                        TextButton(onClick = { showTerms = true }) { Text("Read Terms") }
                                    }
                                }
                            }

                            Button(
                                enabled = !busy && accepted && email.isNotBlank() && password.isNotBlank() &&
                                    (!createAccount || name.isNotBlank()),
                                onClick = {
                                    busy = true
                                    message = ""
                                    lifecycleScope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                if (createAccount) {
                                                    NexusAccountStore.signUp(context, name, email, password)
                                                } else {
                                                    NexusAccountStore.signIn(context, email, password)
                                                }
                                            }
                                            account = NexusAccountStore.account(context)
                                            message = result
                                            error = false
                                            password = ""
                                        } catch (e: Exception) {
                                            message = e.message ?: "Unable to connect to Nexus Account."
                                            error = true
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (busy) "Please wait…" else if (createAccount) "Create Nexus Account" else "Sign in") }

                            Text(
                                "Google sign-in is available after the Google provider and Android redirect URL are enabled in Supabase Auth settings.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        if (message.isNotBlank()) {
                            Text(
                                message,
                                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (showPrivacy) {
                    AlertDialog(
                        onDismissRequest = { showPrivacy = false },
                        title = { Text("Nexus Launcher Privacy Policy") },
                        text = {
                            Text(
                                "Launcher features such as the home screen, app drawer, gestures, and local settings work on-device without an account. If you choose to sign in, your email and account name are processed by Supabase Auth. Online AI requests send the text you submit to the configured Nexus server and AI provider. Do not submit passwords, payment credentials, or other sensitive information to AI chat. Credentials are stored in Android encrypted preferences. This initial policy should be reviewed and published at a permanent public URL before release."
                            )
                        },
                        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Close") } },
                        dismissButton = { TextButton(onClick = { openUrl("https://cpbwiarqlvtlnwbkmpws.supabase.co") }) { Text("Project site") } },
                    )
                }
                if (showTerms) {
                    AlertDialog(
                        onDismissRequest = { showTerms = false },
                        title = { Text("Nexus Launcher Terms & Conditions") },
                        text = {
                            Text(
                                "Use Nexus Launcher lawfully and responsibly. The launcher is provided as-is; feature availability can depend on Android version, device permissions, and third-party services. Online AI may be inaccurate and must not replace professional advice. Third-party providers may apply their own terms. By accepting, you agree to these terms and the privacy notice for this version. These draft terms need legal review and a permanent public URL before public release."
                            )
                        },
                        confirmButton = { TextButton(onClick = { showTerms = false }) { Text("Close") } },
                        dismissButton = { TextButton(onClick = { openUrl("https://cpbwiarqlvtlnwbkmpws.supabase.co") }) { Text("Project site") } },
                    )
                }
            }
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
