package app.lawnchair.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme

/**
 * Lightweight in-app search viewer. It uses Android System WebView, so no browser
 * engine is bundled in the APK and no search API key is required.
 */
class NexusSearchBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = intent.getStringExtra(EXTRA_URL)?.takeIf(::isHttpUrl)
            ?: intent.getStringExtra(EXTRA_QUERY)?.let(::googleSearchUrl)
            ?: googleSearchUrl("")

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                var progress by remember { mutableIntStateOf(0) }
                var pageTitle by remember { mutableStateOf("Nexus Search") }
                var webView by remember { mutableStateOf<WebView?>(null) }
                BackHandler(enabled = webView?.canGoBack() == true) {
                    webView?.goBack()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(pageTitle, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (webView?.canGoBack() == true) webView?.goBack() else finish()
                                }) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { webView?.reload() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload page")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        settings.safeBrowsingEnabled = true
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val uri = request.url
                                            if (uri.scheme == "http" || uri.scheme == "https") return false
                                            return true
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            progress = newProgress
                                        }

                                        override fun onReceivedTitle(view: WebView, title: String?) {
                                            pageTitle = title?.takeIf { it.isNotBlank() } ?: "Nexus Search"
                                        }
                                    }
                                    loadUrl(initialUrl)
                                }
                            },
                            update = { if (it.url.isNullOrBlank()) it.loadUrl(initialUrl) },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // WebView is hosted by Compose; Android releases its view resources with the activity.
        super.onDestroy()
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

    private fun googleSearchUrl(query: String): String =
        "https://www.google.com/search?q=" + Uri.encode(query)

    companion object {
        const val EXTRA_QUERY = "app.lawnchair.assistant.SEARCH_QUERY"
        const val EXTRA_URL = "app.lawnchair.assistant.SEARCH_URL"

        fun queryIntent(context: android.content.Context, query: String): Intent =
            Intent(context, NexusSearchBrowserActivity::class.java).putExtra(EXTRA_QUERY, query)

        fun urlIntent(context: android.content.Context, url: String): Intent =
            Intent(context, NexusSearchBrowserActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
