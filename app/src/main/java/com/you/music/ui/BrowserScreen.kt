package com.you.music.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.you.music.data.Privacy
import java.net.URLEncoder

private val domainRegex = Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$")

private class BrowserTab(val id: Int, val web: WebView) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun homeUrl(google: Boolean, priv: Boolean): String = when {
    priv -> "https://duckduckgo.com/?kl=wt-wt"
    google -> "https://www.google.com/"
    else -> "https://duckduckgo.com/"
}

private fun searchUrl(q: String, google: Boolean, priv: Boolean): String = when {
    priv -> "https://duckduckgo.com/?q=${enc(q)}&kl=wt-wt&ia=web"
    google -> "https://www.google.com/search?q=${enc(q)}"
    else -> "https://duckduckgo.com/?q=${enc(q)}&ia=web"
}

private fun targetUrl(input: String, google: Boolean, priv: Boolean): String {
    val q = input.trim()
    if (q.startsWith("http://") || q.startsWith("https://")) return q
    if (domainRegex.matches(q)) return "https://$q"
    return searchUrl(q, google, priv)
}

/** Page ke URL se search ka word nikalta hai (DuckDuckGo / Google) */
private fun queryOf(url: String): String? = try {
    val u = Uri.parse(url)
    val host = u.host ?: ""
    if (host.contains("duckduckgo.com") || host.contains("google.")) u.getQueryParameter("q")
    else null
} catch (e: Throwable) {
    null
}

private fun displayOf(url: String): String {
    if (url.isBlank()) return ""
    val q = queryOf(url)
    if (!q.isNullOrBlank()) return q
    val u = try {
        Uri.parse(url)
    } catch (e: Throwable) {
        return url
    }
    val host = u.host ?: return url
    val path = u.path ?: ""
    if ((host.contains("duckduckgo.com") || host.contains("google.")) &&
        (path.isEmpty() || path == "/")
    ) return ""
    return url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
}

private fun destroyTab(t: BrowserTab) {
    try {
        t.web.stopLoading()
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        t.web.clearHistory()
        t.web.clearCache(true)
        t.web.destroy()
    } catch (e: Throwable) {
        // ignore
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialQuery: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val priv by Privacy.active.collectAsState()

    var google by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(1) }
    val tabs = remember { mutableStateListOf<BrowserTab>() }
    var cur by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    val makeTab: (String) -> BrowserTab = { startUrl ->
        val wv = WebView(ctx)
        val tab = BrowserTab(nextId, wv)
        nextId += 1

        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setGeolocationEnabled(false)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(false)
        s.cacheMode = if (priv) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

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

            override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                if (u != null) tab.url = u
            }

            override fun onPageFinished(view: WebView, u: String?) {
                if (u != null) tab.url = u
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }

            // Location kabhi nahi
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }

        wv.loadUrl(startUrl)
        tab
    }

    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            val start = if (initialQuery.isNotBlank()) targetUrl(initialQuery, false, priv)
            else homeUrl(false, priv)
            tabs.add(makeTab(start))
        }
    }

    val tab = tabs.getOrNull(cur)

    // Address bar: page badalne par uska naam / search word dikhao
    LaunchedEffect(tab?.id, tab?.url, editing) {
        if (!editing) text = displayOf(tab?.url ?: "")
    }

    // Chip ko page ke engine ke hisaab se rakho
    LaunchedEffect(tab?.url) {
        val u = tab?.url ?: ""
        if (!priv) {
            if (u.contains("google.")) google = true
            else if (u.contains("duckduckgo.com")) google = false
        }
    }

    val go: (String) -> Unit = { input ->
        val t = tabs.getOrNull(cur)
        if (t != null && input.isNotBlank()) {
            t.web.loadUrl(targetUrl(input, google, priv))
            focus.clearFocus()
        }
    }

    // g ko seedha pass karte hain, taaki purani value use na ho
    val switchEngine: (Boolean) -> Unit = { g ->
        google = g
        val t = tabs.getOrNull(cur)
        if (t != null) {
            val q = queryOf(t.url)
            t.web.loadUrl(
                if (!q.isNullOrBlank()) searchUrl(q, g, priv) else homeUrl(g, priv)
            )
        }
    }

    val addTab: () -> Unit = {
        if (tabs.size < 5) {
            tabs.add(makeTab(homeUrl(google, priv)))
            cur = tabs.lastIndex
        }
    }

    val closeTab: (Int) -> Unit = { i ->
        if (i in tabs.indices) {
            val t = tabs.removeAt(i)
            destroyTab(t)
            if (tabs.isEmpty()) {
                onClose()
            } else {
                if (i < cur) cur -= 1
                cur = cur.coerceIn(0, tabs.lastIndex)
            }
        }
    }

    BackHandler {
        val t = tabs.getOrNull(cur)
        if (t != null && t.web.canGoBack()) t.web.goBack()
        else if (tabs.size > 1) closeTab(cur)
        else onClose()
    }

    // Band hote hi history, cookies aur cache sab saaf
    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { destroyTab(it) }
            tabs.clear()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
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
                .padding(start = 4.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { editing = it.isFocused },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search ya website") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(text) })
            )
            IconButton(onClick = { tab?.web?.reload() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }

        // Tabs
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, t ->
                FilterChip(
                    selected = i == cur,
                    onClick = { cur = i },
                    label = { Text(t.title.ifBlank { "Tab ${i + 1}" }.take(14), maxLines = 1) },
                    trailingIcon = {
                        if (tabs.size > 1) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Tab band karo",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { closeTab(i) }
                            )
                        }
                    }
                )
            }
            if (tabs.size < 5) {
                IconButton(onClick = addTab) {
                    Icon(Icons.Rounded.Add, contentDescription = "Naya tab")
                }
            }
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
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !google,
                    onClick = { switchEngine(false) },
                    label = { Text("DuckDuckGo") }
                )
                FilterChip(
                    selected = google,
                    onClick = { switchEngine(true) },
                    label = { Text("Google") }
                )
            }
        }

        if (tab != null) {
            if (tab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            key(tab.id) {
                AndroidView(
                    factory = {
                        (tab.web.parent as? ViewGroup)?.removeView(tab.web)
                        tab.web
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
