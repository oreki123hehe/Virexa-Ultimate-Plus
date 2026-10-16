package com.virexa.ultimate

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONTokener

private val Ink = Color(0xFF0C0B14)
private val Panel = Color(0xFF191725)
private val Violet = Color(0xFF9A78FF)
private val Muted = Color(0xFFB9B5C9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VirexaApp() }
    }
}

private enum class Page(val title: String) {
    Home("Home"), Browser("Browser"), Downloads("Downloads"), Player("Player"), Library("Library"), Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirexaApp() {
    var page by remember { mutableStateOf(Page.Home) }
    val context = LocalContext.current
    var downloadUrl by remember { mutableStateOf("") }
    var browserUrl by remember { mutableStateOf("https://example.com") }
    var message by remember { mutableStateOf("Your multimedia space is ready.") }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMedia = uri
            page = Page.Player
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            onPrimary = Color.White,
            background = Ink,
            surface = Panel,
            onSurface = Color(0xFFF5F2FF),
            secondary = Color(0xFF67DCC5)
        )
    ) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("VIREXA", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("ULTIMATE+", style = MaterialTheme.typography.labelSmall, color = Violet)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    val items = listOf(Page.Home, Page.Browser, Page.Downloads, Page.Player, Page.Library)
                    val icons = listOf(Icons.Default.Home, Icons.Default.Public, Icons.Default.Download, Icons.Default.PlayCircle, Icons.Default.Folder)
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = { Icon(icons[index], contentDescription = item.title) },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, indicatorColor = Color(0xFF30264D))
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (page) {
                    Page.Home -> HomePage(
                        onOpen = { page = it },
                        onPickMedia = { mediaPicker.launch("*/*") },
                        message = message
                    )
                    Page.Browser -> BrowserPage(
                        url = browserUrl,
                        onUrlChange = { browserUrl = it },
                        onDownload = { url ->
                            downloadUrl = url
                            page = Page.Downloads
                        }
                    )
                    Page.Downloads -> DownloadsPage(
                        url = downloadUrl,
                        onUrlChange = { downloadUrl = it },
                        onDownload = { url ->
                            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                            try {
                                val parsedUri = Uri.parse(normalized)
                                val request = DownloadManager.Request(parsedUri)
                                    .setTitle(parsedUri.lastPathSegment ?: "Virexa download")
                                    .setDescription("Downloading with Virexa")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setAllowedOverMetered(true)
                                    .setAllowedOverRoaming(false)
                                val cookie = CookieManager.getInstance().getCookie(normalized)
                                if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
                                request.addRequestHeader("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0")
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, parsedUri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "virexa-download")
                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                manager.enqueue(request)
                                message = "Download added. Check Android notifications for progress."
                            } catch (e: Exception) {
                                message = "Could not start download. Check the URL and try again."
                            }
                        },
                        message = message
                    )
                    Page.Player -> PlayerPage(uri = selectedMedia, onPick = { mediaPicker.launch("video/*") }, context = context)
                    Page.Library -> LibraryPage(onPickVideo = { mediaPicker.launch("video/*") }, onPickAudio = { mediaPicker.launch("audio/*") })
                    Page.Settings -> SettingsPage()
                }
            }
        }
    }
}

@Composable
private fun HomePage(onOpen: (Page) -> Unit, onPickMedia: () -> Unit, message: String) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Everything you love, in one place.", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Browse the web, manage downloads, and play media stored on your device.", color = Muted)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { onOpen(Page.Browser) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Explore, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open browser")
                    }
                    Text(message, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        item { Text("QUICK ACCESS", style = MaterialTheme.typography.labelLarge, color = Violet) }
        item { FeatureRow(Icons.Default.Download, "Smart Downloader", "Download files from direct links that permit access") { onOpen(Page.Downloads) } }
        item { FeatureRow(Icons.Default.PlayCircle, "Media Player", "Play a video or audio file from your device") { onPickMedia() } }
        item { FeatureRow(Icons.Default.Folder, "Media Library", "Open local video and audio files") { onOpen(Page.Library) } }
        item { FeatureRow(Icons.Default.Settings, "Preferences", "App information and privacy-minded defaults") { onOpen(Page.Settings) } }
        item {
            Text("Downloads are supported for direct URLs and sources that authorize downloading. Virexa does not bypass DRM or access restrictions.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF30264D)) {
                Icon(icon, null, tint = Violet, modifier = Modifier.padding(12.dp).size(25.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun BrowserPage(url: String, onUrlChange: (String) -> Unit, onDownload: (String) -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Ready to browse") }
    var loading by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var detectedMedia by remember { mutableStateOf(listOf<String>()) }

    fun normalizedAddress(input: String): String {
        val value = input.trim()
        if (value.isEmpty()) return "https://www.google.com"
        if (value.contains(" ") || (!value.contains(".") && !value.startsWith("http"))) {
            return "https://www.google.com/search?q=" + Uri.encode(value)
        }
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search or enter website") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                IconButton(onClick = {
                    val target = normalizedAddress(url)
                    onUrlChange(target)
                    webView?.loadUrl(target) ?: run { reloadKey++ }
                }) { Icon(Icons.Default.Search, "Search or open") }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
            IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                Icon(Icons.Default.ArrowForward, "Forward")
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {
                if (url.isNotBlank()) onDownload(normalizedAddress(url))
            }, enabled = url.isNotBlank()) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(4.dp))
                Text("Send to downloads")
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet)
        Text(pageTitle, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
        if (detectedMedia.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MEDIA YANG TERDETEKSI (${detectedMedia.size})", color = Violet, style = MaterialTheme.typography.labelMedium)
                    detectedMedia.take(5).forEachIndexed { index, mediaUrl ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(Uri.parse(mediaUrl).lastPathSegment ?: "Media ${index + 1}", color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { onDownload(mediaUrl) }) { Text("Unduh") }
                        }
                    }
                }
            }
        }
        key(reloadKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                                pageTitle = pageUrl ?: "Loading…"
                            }
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                loading = false
                                pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: pageUrl ?: "Page loaded"
                                if (!pageUrl.isNullOrBlank()) onUrlChange(pageUrl)
                                // Detect media URLs exposed by the page itself; this does not bypass DRM.
                                view?.evaluateJavascript(
                                    """(function(){var a=[];document.querySelectorAll('video,audio,video source,audio source').forEach(function(e){var u=e.currentSrc||e.src||e.getAttribute('src');if(u)a.push(u)});document.querySelectorAll('a[href]').forEach(function(e){var u=e.href||'';if(/\.(mp4|webm|m4v|mp3|m4a|aac|ogg|wav)(\?|#|$)/i.test(u))a.push(u)});return JSON.stringify([...new Set(a)].filter(function(u){return /^https?:/i.test(u)}).slice(0,20));})()"""
                                ) { raw ->
                                    try {
                                        val parsed = JSONTokener(raw).nextValue()
                                        val array = JSONArray(parsed as? String ?: "[]")
                                        detectedMedia = (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.startsWith("http://") || value.startsWith("https://") } }.distinct()
                                    } catch (_: Exception) {
                                        detectedMedia = emptyList()
                                    }
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        setDownloadListener { downloadUrl, _, _, _, _ ->
                            if (!downloadUrl.isNullOrBlank() &&
                                (downloadUrl.startsWith("https://") || downloadUrl.startsWith("http://"))) {
                                onDownload(downloadUrl)
                            }
                        }
                        loadUrl(normalizedAddress(url))
                    }
                },
                update = { view ->
                    webView = view
                }
            )
        }
    }
}

@Composable
private fun DownloadsPage(url: String, onUrlChange: (String) -> Unit, onDownload: (String) -> Unit, message: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("DOWNLOAD MANAGER", style = MaterialTheme.typography.labelLarge, color = Violet)
        Text("Start a download from a direct file URL. Android manages the transfer and completion notification.", color = Muted)
        OutlinedTextField(value = url, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("Direct download URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Button(onClick = { if (url.isNotBlank()) onDownload(url) }, modifier = Modifier.fillMaxWidth(), enabled = url.startsWith("https://") || url.startsWith("http://")) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Start download")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = Violet)
                Spacer(Modifier.width(10.dp))
                Text(message, color = Muted)
            }
        }
        Text("Current transfers and completed files are also available in Android's Downloads notification and system download view.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlayerPage(uri: Uri?, onPick: () -> Unit, context: Context) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MEDIA PLAYER", style = MaterialTheme.typography.labelLarge, color = Violet)
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose a video") }
        if (uri == null) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, null, tint = Violet, modifier = Modifier.size(64.dp))
                    Text("Choose a local video to play", color = Muted)
                }
            }
        } else {
            val player = remember(uri) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = false
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun LibraryPage(onPickVideo: () -> Unit, onPickAudio: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("MEDIA LIBRARY", style = MaterialTheme.typography.labelLarge, color = Violet)
        Text("Choose local media using Android's system file picker. Access is limited to files you select.", color = Muted)
        FeatureRow(Icons.Default.VideoLibrary, "Browse videos", "Choose a video file to play", onPickVideo)
        FeatureRow(Icons.Default.LibraryMusic, "Browse audio", "Choose an audio file", onPickAudio)
        Text("More library features—automatic indexing, playlists, and sorting—can be added on top of this foundation.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsPage() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = Violet)
        FeatureRow(Icons.Default.Palette, "Appearance", "Virexa uses a dark interface with violet accents") {}
        FeatureRow(Icons.Default.Security, "Privacy", "No Virexa account or remote profile is required in this foundation") {}
        FeatureRow(Icons.Default.Info, "About Virexa", "Ultimate+ foundation • Version 1.0.0") {}
        Text("Advertising is not enabled in this build. Ad network selection and approval are required before monetization.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
