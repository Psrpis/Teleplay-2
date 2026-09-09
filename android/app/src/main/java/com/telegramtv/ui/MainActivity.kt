package com.telegramtv.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.Window
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.telegramtv.data.repository.SettingsRepository
import com.telegramtv.ui.theme.TelePlayMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private var webView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenContainer: FrameLayout? = null
    private var showUrlSetup by mutableStateOf(false)
    private var configuredUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompatCompat.configure(this)
        setContent {
            TelePlayMobileTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showUrlSetup || configuredUrl == null) {
                        ServerSetupScreen(
                            initialUrl = configuredUrl.orEmpty(),
                            onSave = { url ->
                                lifecycleScope.launch {
                                    settingsRepository.setServerUrl(url)
                                    configuredUrl = url.trimEnd('/')
                                    showUrlSetup = false
                                }
                            }
                        )
                    } else {
                        WebShell(
                            url = configuredUrl!!,
                            onChangeServer = { showUrlSetup = true },
                            onWebViewReady = { webView = it }
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            configuredUrl = settingsRepository.getConfiguredServerUrl()?.trimEnd('/')
        }
    }

    @Composable
    private fun WebShell(url: String, onChangeServer: () -> Unit, onWebViewReady: (WebView) -> Unit) {
        var refreshLayout: SwipeRefreshLayout? by remember { mutableStateOf(null) }
        var currentWebView: WebView? by remember { mutableStateOf(null) }

        DisposableEffect(url) {
            onDispose {
                currentWebView?.stopLoading()
                currentWebView?.destroy()
                currentWebView = null
            }
        }

        BackHandler {
            when {
                fullscreenView != null -> hideFullscreen()
                currentWebView?.canGoBack() == true -> currentWebView?.goBack()
                else -> finish()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val swipe = SwipeRefreshLayout(context).apply {
                        setOnRefreshListener { currentWebView?.reload() }
                    }
                    val view = WebView(context).apply {
                        configureWebView(url)
                        setOnScrollChangeListener { _, _, scrollY, _, _ -> swipe.isEnabled = scrollY == 0 }
                        webView = this
                        currentWebView = this
                        onWebViewReady(this)
                    }
                    swipe.addView(view, FrameLayout.LayoutParams(-1, -1))
                    refreshLayout = swipe
                    swipe
                },
                update = { swipe ->
                    refreshLayout = swipe
                    swipe.isRefreshing = false
                }
            )
            FloatingActionButton(
                onClick = onChangeServer,
                modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Change server")
            }
        }
    }

    private fun WebView.configureWebView(url: String) {
        val host = runCatching { URI(url).host }.getOrNull()
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                val sameHost = target.host == host || target.host == null
                if (sameHost) return false
                startActivity(Intent(Intent.ACTION_VIEW, target))
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                (view.parent as? SwipeRefreshLayout)?.isRefreshing = false
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView, callback: android.webkit.ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                return super.onShowFileChooser(webView, callback, params)
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                fullscreenView = view
                fullscreenContainer = FrameLayout(this@MainActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                addContentView(fullscreenContainer, FrameLayout.LayoutParams(-1, -1))
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        setDownloadListener(DownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(downloadUrl)?.let { addRequestHeader("Cookie", it) }
                setTitle(DownloadManager.Request(Uri.parse(downloadUrl)).let { "TelePlay download" })
                setDescription("Downloading media from TelePlay")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, contentDisposition.substringAfter("filename=", "teleplay-download"))
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
        })
        loadUrl(url)
    }

    private fun hideFullscreen() {
        fullscreenContainer?.removeAllViews()
        fullscreenContainer?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        fullscreenContainer = null
        fullscreenView = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onDestroy() {
        webView?.stopLoading()
        webView?.destroy()
        super.onDestroy()
    }
}

@Composable
private fun ServerSetupScreen(initialUrl: String, onSave: (String) -> Unit) {
    var value by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect to TelePlay", style = MaterialTheme.typography.headlineMedium)
        Text("Enter your self-hosted server address to open the full TelePlay web experience.", modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.100:8000") },
            supportingText = { error?.let { Text(it) } }
        )
        Button(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            onClick = {
                val normalized = value.trim().trimEnd('/')
                if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) error = "URL must start with http:// or https://"
                else onSave(normalized)
            }
        ) { Text("Open TelePlay") }
    }
}

private object WindowCompatCompat {
    fun configure(activity: ComponentActivity) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }
}
