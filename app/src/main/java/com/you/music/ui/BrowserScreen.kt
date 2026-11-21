package com.you.music.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.you.music.data.Privacy
import java.net.URLEncoder

private val domainRegex = Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$")

private fun targetUrl(input: String, google: Boolean, priv: Boolean): String {
    val q = input.trim()
    if (q.startsWith("http://") || q.startsWith("https://")) return q
    if (domainRegex.matches(q)) return "https://$q"
    val e = URLEncoder.encode(q, "UTF-8")
    return when {
        priv -> "https://duckduckgo.com/?q=$e&kl=wt-wt&ia=web"
        google -> "https://www.google.com/search?q=$e"
        else -> "https://duckduckgo.com/?q=$e&ia=web"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialQuery: String, onClose: () -> Unit) {
    val priv by Privacy.active.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    var google by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val webRef = remember { arrayOfNulls<WebView>(1) }

    val go: () -> Unit = {
        if (query.isNotBlank()) {
            webRef[0]?.loadUrl(targetUrl(query, google, priv))
        }
    }

    BackHandler {
        val wv = webRef[0]
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    // Band hote hi history, cookies aur cache sab saaf
    DisposableEffect(Unit) {
        onDispose {
            webRef[0]?.let { wv ->
                wv.stopLoading()
                wv.clearHistory()
                wv.clearCache(true)
                wv.clearFormData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                wv.destroy()
            }
            webRef[0] = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search ya website") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() })
            )
        }

        if (priv) {
            Text(
                "Private browsing: sirf DuckDuckGo, location block, band hote hi sab saaf",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !google,
                    onClick = {
                        google = false
                        go()
                    },
                    label = { Text("DuckDuckGo") }
                )
                FilterChip(
                    selected = google,
                    onClick = {
                        google = true
                        go()
                    },
                    label = { Text("Google") }
                )
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AndroidView(
            factory = { c ->
                WebView(c).also { wv ->
                    webRef[0] = wv
                    val s = wv.settings
                    s.javaScriptEnabled = true
                    s.domStorageEnabled = true
                    s.setGeolocationEnabled(false)
                    s.allowFileAccess = false
                    s.allowContentAccess = false
                    s.setSupportMultipleWindows(false)
                    s.cacheMode =
                        if (priv) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(!priv)
                    cm.setAcceptThirdPartyCookies(wv, false)

                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val sch = request.url.scheme
                            return sch != "http" && sch != "https"
                        }
                    }
                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        // Location kabhi nahi: website poochegi to bhi "na"
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String,
                            callback: GeolocationPermissions.Callback
                        ) {
                            callback.invoke(origin, false, false)
                        }

                        // Camera / mic bhi band
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.deny()
                        }
                    }

                    if (initialQuery.isNotBlank()) {
                        wv.loadUrl(targetUrl(initialQuery, false, priv))
                    } else {
                        wv.loadUrl(
                            if (priv) "https://duckduckgo.com/?kl=wt-wt"
                            else "https://duckduckgo.com/"
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
