package com.virexa.ultimate

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Inspects direct, accessible media URLs. Does not bypass DRM or access controls. */
internal object MediaSourceResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    sealed class Result {
        data class Media(val url: String, val fileName: String, val mimeType: String, val extension: String) : Result()
        data class StreamManifest(val kind: String) : Result()
        data object NotMedia : Result()
        data class Failure(val reason: String) : Result()
    }

    fun inspect(rawUrl: String): Result = try {
        val request = Request.Builder().url(rawUrl)
            .header("User-Agent", System.getProperty("http.agent") ?: "Mozilla/5.0")
            .header("Range", "bytes=0-0").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) return Result.Failure("HTTP ${response.code}")
            val finalUrl = response.request.url.toString()
            val mime = response.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase().orEmpty()
            val disposition = response.header("Content-Disposition").orEmpty()
            val headerName = Regex("""filename\*?=(?:UTF-8''|")?([^;"]+)""", RegexOption.IGNORE_CASE)
                .find(disposition)?.groupValues?.getOrNull(1)?.let {
                    runCatching { URLDecoder.decode(it.trim().trim('"'), "UTF-8") }.getOrDefault(it.trim().trim('"'))
                }
            val pathName = response.request.url.encodedPath.substringAfterLast('/').takeIf { it.isNotBlank() }
            val pathLower = response.request.url.encodedPath.lowercase()
            if (mime.contains("mpegurl") || pathLower.endsWith(".m3u8")) return Result.StreamManifest("HLS")
            if (mime.contains("dash+xml") || pathLower.endsWith(".mpd")) return Result.StreamManifest("DASH")
            if (mime == "text/html" || mime == "application/xhtml+xml" || mime.startsWith("text/")) return Result.NotMedia

            val pathExt = pathName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 8 }?.lowercase()
            val mimeExt = when (mime) {
                "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/3gpp" -> "3gp"
                "video/quicktime" -> "mov"; "audio/mpeg" -> "mp3"; "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/aac" -> "aac"; "audio/ogg", "application/ogg" -> "ogg"
                "audio/wav", "audio/x-wav" -> "wav"; "video/x-matroska" -> "mkv"
                else -> null
            }
            val mediaMime = mime.startsWith("video/") || mime.startsWith("audio/") ||
                mime == "application/octet-stream" || mime == "binary/octet-stream"
            val mediaExt = pathExt in setOf("mp4", "webm", "m4v", "mov", "3gp", "mkv", "mp3", "m4a", "aac", "ogg", "wav", "flac")
            if (!mediaMime && !mediaExt) return Result.NotMedia
            val ext = mimeExt ?: pathExt ?: "bin"
            val name = (headerName?.takeIf { it.isNotBlank() } ?: pathName?.takeIf { it.contains('.') } ?: "virexa-media.$ext")
                .replace(Regex("""[\\/:*?"<>|]"""), "_").take(140).ifBlank { "virexa-media.$ext" }
            Result.Media(finalUrl, name, mime.ifBlank { "application/octet-stream" }, ext)
        }
    } catch (e: Exception) {
        Result.Failure(e.message?.take(100) ?: "koneksi gagal")
    }
}
