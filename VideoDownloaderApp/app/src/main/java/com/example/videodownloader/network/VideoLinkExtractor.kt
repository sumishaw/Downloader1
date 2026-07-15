package com.example.videodownloader.network
import com.example.videodownloader.model.VideoInfo
import com.example.videodownloader.model.ExtractResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI

// Add this to your project, either in the same file or in a separate file
// (e.g., com.example.videodownloader.model.ExtractResult)

sealed class ExtractResult {
    data class Found(val videos: List<VideoInfo>) : ExtractResult()
    data class NoneFound(val message: String) : ExtractResult()
}


class VideoLinkExtractor(private val client: OkHttpClient) {
fun extract(pageUrl: String): ExtractResult {
    val html = fetchHtml(pageUrl) ?: return ExtractResult.NoneFound(
        "Unable to fetch the page. Check the URL or your internet connection."
    )
    return extractVideos(pageUrl, html)
}
        fun extractVideos(pageUrl: String, html: String): ExtractResult {
        val doc = Jsoup.parse(html, pageUrl)
        val found = LinkedHashSet<String>()

        // <video src="...">
        doc.select("video[src]").forEach { found.add(it.attr("abs:src")) }
        // <video><source src="..."></video>
        doc.select("video source[src]").forEach { found.add(it.attr("abs:src")) }
        // <meta property="og:video" content="...">
        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]")
            .forEach { found.add(it.attr("abs:content")) }
        // Direct <a> links to common video file types
        doc.select("a[href~=(?i)\\.(mp4|webm|mov|m3u8)(\\?.*)?$]")
            .forEach { found.add(it.attr("abs:href")) }

        val cleaned = found.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) {
            return ExtractResult.NoneFound(
                "No openly-served video file was found on this page. If the site plays " +
                    "video through a private/streaming player without a direct file link, " +
                    "this app intentionally won't try to extract it."
            )
        }

        val videos = cleaned.mapNotNull { url -> probeVideo(pageUrl, url) }
        return if (videos.isEmpty()) {
            ExtractResult.NoneFound("Found video links, but none were publicly downloadable.")
        } else {
            ExtractResult.Found(videos)
        }
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    /** HEAD request to confirm the file is public (200, no auth) and check range support. */
    private fun probeVideo(pageUrl: String, directUrl: String): VideoInfo? {
        val request = Request.Builder().url(directUrl).head().header("User-Agent", USER_AGENT).build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) return@use null // auth required, skip
                if (!resp.isSuccessful && resp.code != 206) return@use null

                val contentType = resp.header("Content-Type")
                val length = resp.header("Content-Length")?.toLongOrNull()
                val acceptsRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true

                VideoInfo(
                    pageUrl = pageUrl,
                    directUrl = directUrl,
                    suggestedFileName = fileNameFromUrl(directUrl),
                    mimeType = contentType,
                    sizeBytes = length,
                    supportsRangeRequests = acceptsRanges
                )
            }
        }.getOrNull()
    }

    private fun fileNameFromUrl(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        val name = path.substringAfterLast('/').ifBlank { "video" }
        return if (name.contains('.')) name else "$name.mp4"
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
