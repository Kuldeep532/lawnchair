package app.lawnchair.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

/**
 * Browser-only cloud chat. Supabase password authentication and chat requests run in WebView.
 * No local model, native Google sign-in, or embedded provider API key is used.
 */
class NexusChatBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                var progress by remember { mutableIntStateOf(0) }
                var title by remember { mutableStateOf("Nexus Chat") }
                var loggedIn by remember { mutableStateOf(false) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("Sign in with your email and password to use Nexus Chat.") }
                var busy by remember { mutableStateOf(false) }

                BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (webView?.canGoBack() == true) webView?.goBack() else finish()
                                }) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                if (loggedIn) {
                                    IconButton(onClick = { webView?.evaluateJavascript("window.nexusSignOut && window.nexusSignOut()", null) }) {
                                        Icon(Icons.Rounded.Logout, contentDescription = "Sign out")
                                    }
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email address") },
                            singleLine = true,
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
                                busy = true
                                webView?.evaluateJavascript(
                                    "window.nexusSignIn && window.nexusSignIn(" +
                                        "'${email.jsString()}', '${password.jsString()}')",
                                ) { result ->
                                    busy = false
                                    message = result?.trim('"')?.takeIf { it.isNotBlank() } ?: "The sign-in request could not be completed."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && email.isNotBlank() && password.isNotBlank() && webView != null,
                        ) { Text(if (busy) "Please wait…" else "Sign in") }
                        Text(message)
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { context ->
                                createChatWebView(context) { newProgress, pageTitle ->
                                    progress = newProgress
                                    title = pageTitle
                                }.also {
                                    webView = it
                                    it.loadUrl(CHAT_PAGE_URL)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val CHAT_PAGE_URL = "file:///android_asset/nexus-chat/index.html"

        fun intent(context: Context): Intent =
            Intent(context, NexusChatBrowserActivity::class.java)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createChatWebView(
    context: Context,
    onPageState: (Int, String) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = true
    settings.allowContentAccess = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.scheme != "https" && request.url.scheme != "http" && request.url.scheme != "file"

        override fun onPageFinished(view: WebView, url: String?) {
            onPageState(100, view.title?.takeIf { it.isNotBlank() } ?: "Nexus Chat")
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onPageState(newProgress, view.title?.takeIf { it.isNotBlank() } ?: "Nexus Chat")
        }
    }
}

private fun String.jsString(): String =
    replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
