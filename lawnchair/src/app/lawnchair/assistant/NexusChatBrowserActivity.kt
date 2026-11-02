package app.lawnchair.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> createChatWebView(context).apply { loadUrl(CHAT_PAGE_URL) } },
                )
            }
        }
    }

    companion object {
        private const val CHAT_PAGE_URL = "https://appassets.androidplatform.net/assets/nexus-chat/index.html"

        fun intent(context: Context): Intent =
            Intent(context, NexusChatBrowserActivity::class.java)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createChatWebView(context: Context): WebView = WebView(context).apply {
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
    val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
        .addPathHandler(
            "/assets/",
            androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context),
        )
        .build()
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): android.webkit.WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.scheme != "https" && request.url.scheme != "http"
    }
    loadUrl(CHAT_PAGE_URL)
}
