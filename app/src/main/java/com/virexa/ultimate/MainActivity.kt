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
import androidx.compose.foundation.lazy.items
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

private val Ink = Color(0xFF0B0912)
private val Panel = Color(0xFF191525)
private val Violet = Color(0xFFAA8CFF)
private val Mint = Color(0xFF67E2C5)
private val Muted = Color(0xFFBDB6CF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VirexaApp() }
    }
}

private enum class Page(val label: String) { Home("Home"), Browser("Browser"), Downloads("Downloads"), Player("Player"), Library("Library"), Settings("Settings") }

private fun normalizeAddress(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "https://www.google.com"
    if (value.contains(" ") || (!value.contains(".") && !value.startsWith("http"))) return "https://www.google.com/search?q=" + Uri.encode(value)
    return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirexaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("virexa", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf(Page.Home) }
    var browserAddress by remember { mutableStateOf("https://www.google.com") }
    var downloadAddress by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("Selamat datang di Virexa.") }
    var chosenMedia by remember { mutableStateOf<Uri?>(null) }
    var refreshDownloads by remember { mutableIntStateOf(0) }
    val tabs = remember { mutableStateListOf("https://www.google.com") }
    var tabIndex by remember { mutableIntStateOf(0) }
    val bookmarks = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()) } }
    val history = remember { mutableStateListOf<String>().apply { addAll(prefs.getString("history", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()) } }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { chosenMedia = uri; page = Page.Player } }

    fun startDownload(raw: String) {
        val url = raw.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            notice = "Tautan tidak valid. Gunakan URL HTTP atau HTTPS."; return
        }
        notice = "Memeriksa sumber media…"
        Thread {
            val result = MediaSourceResolver.inspect(url)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                when (result) {
                    is MediaSourceResolver.Result.Media -> {
                        try {
                            val uri = Uri.parse(result.url)
                            val safeName = result.fileName.replace(Regex("[\\\\/:*?\\\"<>|]"), "_").take(140)
                            val request = DownloadManager.Request(uri)
                                .setTitle(safeName)
                                .setDescription("Media • Virexa")
                                .setMimeType(result.mimeType)
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
                            request.addRequestHeader("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0")
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                            notice = "Unduhan media dimulai: $safeName"
                            refreshDownloads++
                        } catch (_: Exception) {
                            notice = "Unduhan gagal dimulai. Periksa izin, URL, atau akses sumber."
                        }
                    }
                    is MediaSourceResolver.Result.StreamManifest ->
                        notice = "Sumber streaming ${result.kind} terdeteksi, tetapi belum didukung mesin unduhan ini. DRM tidak dibypass."
                    is MediaSourceResolver.Result.NotMedia ->
                        notice = "Tautan ini tampaknya halaman web, bukan berkas media langsung. Buka halaman di Browser dan pilih media yang terdeteksi."
                    is MediaSourceResolver.Result.Failure ->
                        notice = "Sumber tidak bisa diperiksa: ${result.reason}"
                }
            }
        }.start()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Mint, background = Ink, surface = Panel, onSurface = Color.White)) {
        Scaffold(containerColor = Ink, topBar = {
            TopAppBar(title = { Column { Text("VIREXA", color = Color.White, style = MaterialTheme.typography.titleLarge); Text("ULTIMATE+", color = Violet, style = MaterialTheme.typography.labelSmall) } },
                actions = { IconButton(onClick = { page = Page.Settings }) { Icon(Icons.Default.Settings, "Pengaturan", tint = Muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink))
        }, bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf(Page.Home, Page.Browser, Page.Downloads, Page.Player, Page.Library).forEach { item ->
                    val icon = when (item) { Page.Home -> Icons.Default.Home; Page.Browser -> Icons.Default.Public; Page.Downloads -> Icons.Default.Download; Page.Player -> Icons.Default.PlayCircle; else -> Icons.Default.Folder }
                    NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(icon, item.label) }, label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Violet, indicatorColor = Color(0xFF30264D)))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.Home -> HomePage(notice, { page = it }, { mediaPicker.launch("*/*") }, { browserAddress = normalizeAddress(it); tabs[tabIndex] = browserAddress; page = Page.Browser }, bookmarks)
                    Page.Browser -> BrowserPage(
                        address = tabs.getOrElse(tabIndex) { browserAddress }, tabCount = tabs.size,
                        onNewTab = { if (tabs.size < 8) { tabs.add("https://www.google.com"); tabIndex = tabs.lastIndex; browserAddress = tabs[tabIndex] } else notice = "Maksimal 8 tab." },
                        onAddress = { browserAddress = it; tabs[tabIndex] = it },
                        onDownload = { downloadAddress = it; startDownload(it) },
                        onVisited = { url -> if (url.startsWith("http")) { history.remove(url); history.add(0, url); while (history.size > 100) history.removeAt(history.lastIndex); prefs.edit().putString("history", history.joinToString("\n")).apply() } },
                        onBookmark = { url -> if (!bookmarks.contains(url)) { bookmarks.add(url); prefs.edit().putStringSet("bookmarks", bookmarks.toSet()).apply(); notice = "Bookmark disimpan." } else notice = "Bookmark sudah tersimpan." }
                    )
                    Page.Downloads -> DownloadsPage(downloadAddress, { downloadAddress = it }, { startDownload(it) }, notice, refreshDownloads)
                    Page.Player -> PlayerPage(chosenMedia, { mediaPicker.launch("*/*") }, context)
                    Page.Library -> LibraryPage({ mediaPicker.launch("video/*") }, { mediaPicker.launch("audio/*") }, { mediaPicker.launch("*/*") })
                    Page.Settings -> SettingsPage(bookmarks.size, history.size, { history.clear(); prefs.edit().remove("history").apply(); notice = "Riwayat dihapus." })
                }
            }
        }
    }
}

@Composable
private fun HomePage(notice: String, onOpen: (Page) -> Unit, onPick: () -> Unit, onSearch: (String) -> Unit, bookmarks: List<String>) {
    var query by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Your multimedia, one place.", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("Jelajahi web, deteksi media yang tersedia, unduh berkas, dan putar koleksi lokal.", color = Muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari atau alamat situs") },
                        trailingIcon = { IconButton(onClick = { onSearch(query) }) { Icon(Icons.Default.Search, "Cari") } })
                    Button(onClick = { onSearch(query) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Explore, null); Spacer(Modifier.width(8.dp)); Text("Jelajahi web") }
                    Text(notice, color = Mint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("FITUR UTAMA", color = Violet, style = MaterialTheme.typography.labelLarge) }
        item { FeatureRow(Icons.Default.Public, "Browser multimedia", "Tab, bookmark, riwayat, dan deteksi media") { onOpen(Page.Browser) } }
        item { FeatureRow(Icons.Default.Download, "Download manager", "Unduh tautan langsung dan lihat progres") { onOpen(Page.Downloads) } }
        item { FeatureRow(Icons.Default.PlayCircle, "Pemutar media", "Putar audio/video dari perangkat") { onPick() } }
        item { FeatureRow(Icons.Default.Folder, "Pustaka media", "Buka koleksi audio dan video lokal") { onOpen(Page.Library) } }
        if (bookmarks.isNotEmpty()) item { Text("BOOKMARK TERSIMPAN: " + bookmarks.size, color = Violet) }
        item { Text("Deteksi bergantung pada cara situs menyajikan media. DRM dan pembatasan akses tidak dibypass.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        item { Text("Iklan belum aktif; perlu integrasi SDK dan persetujuan jaringan iklan.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFF30264D)) { Icon(icon, null, tint = Violet, modifier = Modifier.padding(11.dp).size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun BrowserPage(address: String, tabCount: Int, onNewTab: () -> Unit, onAddress: (String) -> Unit, onDownload: (String) -> Unit, onVisited: (String) -> Unit, onBookmark: (String) -> Unit) {
    var field by remember(address) { mutableStateOf(address) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("Browser siap") }
    var loading by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(field, { field = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Cari atau URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = { IconButton(onClick = { val u = normalizeAddress(field); field = u; onAddress(u); view?.loadUrl(u) }) { Icon(Icons.Default.Search, "Buka") } })
            IconButton(onClick = onNewTab) { Icon(Icons.Default.Add, "Tab baru ($tabCount)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { if (view?.canGoBack() == true) view?.goBack() }, enabled = view?.canGoBack() == true) { Icon(Icons.Default.ArrowBack, "Kembali") }
            IconButton(onClick = { view?.reload() }) { Icon(Icons.Default.Refresh, "Muat ulang") }
            IconButton(onClick = { if (view?.canGoForward() == true) view?.goForward() }, enabled = view?.canGoForward() == true) { Icon(Icons.Default.ArrowForward, "Maju") }
            IconButton(onClick = { onBookmark(field) }) { Icon(Icons.Default.BookmarkAdd, "Bookmark") }
            IconButton(onClick = { onDownload(field) }) { Icon(Icons.Default.Download, "Unduh URL") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Violet)
        Text(title, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        if (detected.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("MEDIA TERDETEKSI (" + detected.size + ")", color = Mint, style = MaterialTheme.typography.labelMedium)
                detected.take(4).forEach { media ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Uri.parse(media).lastPathSegment ?: "Media", color = Muted, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDownload(media) }) { Text("Unduh") }
                    }
                }
            }
        }
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                view = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    // Detect common media requests while the page is loading, including stream manifests.
                    override fun shouldInterceptRequest(v: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString().orEmpty()
                        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault("")
                        val extension = path.substringAfterLast('/', "").substringAfterLast('.', "")
                        if ((url.startsWith("https://") || url.startsWith("http://")) &&
                            extension in setOf("mp4", "webm", "m4v", "mov", "3gp", "mkv", "mp3", "m4a", "aac", "ogg", "wav", "flac", "m3u8", "mpd")) {
                            v?.post { if (!detected.contains(url)) detected = (detected + url).takeLast(30) }
                        }
                        return super.shouldInterceptRequest(v, request)
                    }
                    override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true; detected = emptyList(); if (!url.isNullOrBlank()) { title = url; field = url; onAddress(url) } }
                    override fun onPageFinished(v: WebView?, url: String?) {
                        loading = false
                        title = v?.title?.takeIf { it.isNotBlank() } ?: url ?: "Halaman"
                        if (!url.isNullOrBlank()) { field = url; onAddress(url); onVisited(url) }
                        v?.evaluateJavascript("""(function(){var a=[];document.querySelectorAll('video,audio,video source,audio source').forEach(function(e){var u=e.currentSrc||e.src||e.getAttribute('src');if(u)a.push(u)});document.querySelectorAll('a[href]').forEach(function(e){var u=e.href||'';if(/\.(mp4|webm|m4v|mp3|m4a|aac|ogg|wav)(\?|#|$)/i.test(u))a.push(u)});return JSON.stringify([...new Set(a)].filter(function(u){return /^https?:/i.test(u)}).slice(0,20));})()""") { raw ->
                            try { val parsed = JSONTokener(raw).nextValue(); val array = JSONArray(parsed as? String ?: "[]"); detected = (detected + (0 until array.length()).mapNotNull { array.optString(it).takeIf { u -> u.startsWith("http://") || u.startsWith("https://") } }).distinct().takeLast(30) }
                            catch (_: Exception) { detected = emptyList() }
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() { override fun onReceivedTitle(v: WebView?, newTitle: String?) { if (!newTitle.isNullOrBlank()) title = newTitle } }
                setDownloadListener { url, _, _, _, _ -> if (!url.isNullOrBlank()) onDownload(url) }
                loadUrl(normalizeAddress(address))
            }
        }, modifier = Modifier.fillMaxSize(), update = { webView -> view = webView })
    }
}

private data class DownloadRow(val id: Long, val title: String, val status: String, val done: Long, val total: Long)
private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format("%.1f MB", bytes / (1024f * 1024f))

@Composable
private fun DownloadsPage(url: String, onUrl: (String) -> Unit, onDownload: (String) -> Unit, notice: String, refreshKey: Int) {
    val context = LocalContext.current
    var records by remember { mutableStateOf(listOf<DownloadRow>()) }
    LaunchedEffect(refreshKey) {
        val list = mutableListOf<DownloadRow>()
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = manager.query(DownloadManager.Query())
            cursor.use {
                val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val doneCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                while (it.moveToNext()) {
                    val total = it.getLong(totalCol); val done = it.getLong(doneCol)
                    val status = when (it.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> "Selesai"; DownloadManager.STATUS_FAILED -> "Gagal"; DownloadManager.STATUS_RUNNING -> "Berjalan"; DownloadManager.STATUS_PAUSED -> "Dijeda"; else -> "Menunggu"
                    }
                    list.add(DownloadRow(it.getLong(idCol), it.getString(titleCol) ?: "Unduhan", status, done, total))
                }
            }
        } catch (_: Exception) { }
        records = list.reversed()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DOWNLOAD MANAGER", color = Violet, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(url, onUrl, modifier = Modifier.fillMaxWidth(), label = { Text("Tautan langsung HTTP/HTTPS") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Button(onClick = { onDownload(url) }, enabled = url.startsWith("https://") || url.startsWith("http://"), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Mulai unduh") }
        Text(notice, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("AKTIVITAS UNDUHAN", color = Violet, style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(records, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, maxLines = 2); Text(item.status, color = Mint, style = MaterialTheme.typography.labelSmall)
                        if (item.total > 0) {
                            LinearProgressIndicator(progress = { (item.done.toFloat() / item.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Violet)
                            Text(formatBytes(item.done) + " / " + formatBytes(item.total), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPage(uri: Uri?, onPick: () -> Unit, context: Context) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MEDIA PLAYER", color = Violet, style = MaterialTheme.typography.labelLarge)
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Pilih audio atau video") }
        if (uri == null) Box(Modifier.fillMaxWidth().weight(1f).background(Panel, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PlayCircle, null, tint = Violet, modifier = Modifier.size(64.dp)); Text("Pilih media dari perangkat", color = Muted) }
        } else {
            val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true } }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player?.let { this.player = it }; useController = true } }, modifier = Modifier.fillMaxWidth().weight(1f))
            Text(uri.lastPathSegment ?: "Media terpilih", color = Muted)
        }
    }
}

@Composable
private fun LibraryPage(onVideo: () -> Unit, onAudio: () -> Unit, onAll: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PUSTAKA MEDIA", color = Violet, style = MaterialTheme.typography.labelLarge)
        Text("Pilih media lokal melalui pemilih berkas Android.", color = Muted)
        FeatureRow(Icons.Default.VideoLibrary, "Video", "Pilih video dari perangkat", onVideo)
        FeatureRow(Icons.Default.LibraryMusic, "Audio", "Pilih musik dari perangkat", onAudio)
        FeatureRow(Icons.Default.FolderOpen, "Semua berkas", "Buka pemilih berkas", onAll)
    }
}

@Composable
private fun SettingsPage(bookmarks: Int, history: Int, onClearHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PENGATURAN", color = Violet, style = MaterialTheme.typography.labelLarge)
        FeatureRow(Icons.Default.Bookmark, "Bookmark", bookmarks.toString() + " alamat tersimpan") {}
        FeatureRow(Icons.Default.History, "Riwayat browser", history.toString() + " halaman tersimpan") {}
        FeatureRow(Icons.Default.DeleteSweep, "Hapus riwayat", "Hapus riwayat lokal", onClearHistory)
        FeatureRow(Icons.Default.Security, "Privasi", "Bookmark dan riwayat disimpan di perangkat") {}
        Text("Iklan belum terhubung. Integrasi SDK, konfigurasi dan persetujuan jaringan iklan masih diperlukan.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Virexa Ultimate+ • Multimedia", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
