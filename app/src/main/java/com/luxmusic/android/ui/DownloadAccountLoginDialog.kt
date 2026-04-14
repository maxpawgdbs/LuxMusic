package com.luxmusic.android.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxmusic.android.data.DownloadService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadAccountLoginDialog(
    service: DownloadService,
    onDismiss: () -> Unit,
    onComplete: (String?) -> Unit,
) {
    var currentUrl by remember(service) { mutableStateOf(service.loginUrl) }
    var loadProgress by remember(service) { mutableIntStateOf(0) }
    var webView by remember(service) { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) {
        val browser = webView
        if (browser != null && browser.canGoBack()) {
            browser.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(service) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Вход: ${service.title}") },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    val browser = webView
                                    if (browser != null && browser.canGoBack()) {
                                        browser.goBack()
                                    } else {
                                        onDismiss()
                                    }
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { onComplete(webView?.settings?.userAgentString) },
                            ) {
                                Text("Готово")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        text = "Войдите в ${service.title} прямо в приложении. После завершения нажмите «Готово», и LuxMusic сам сохранит сессию.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = currentUrl,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp),
                    ) {
                        LoginWebView(
                            initialUrl = service.loginUrl,
                            onCreated = { webView = it },
                            onUrlChanged = { currentUrl = it },
                            onProgressChanged = { loadProgress = it },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    initialUrl: String,
    onCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.loadsImagesAutomatically = true
                settings.allowContentAccess = true
                settings.allowFileAccess = false
                settings.setSupportMultipleWindows(false)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        return !target.startsWith("http://") &&
                            !target.startsWith("https://") &&
                            target != "about:blank"
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        onUrlChanged(url ?: initialUrl)
                        onProgressChanged(0)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        CookieManager.getInstance().flush()
                        onUrlChanged(url ?: initialUrl)
                        onProgressChanged(100)
                    }
                }

                loadUrl(initialUrl)
                onCreated(this)
            }
        },
    )
}
