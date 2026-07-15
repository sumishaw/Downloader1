package com.example.videodownloader.network

import com.example.videodownloader.model.VideoInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.regex.Pattern

// Add this to your project, either in the same file or in a separate file
// (e.g., com.example.videodownloader.model.ExtractResult)

sealed class ExtractResult {
    data class Found(val videos: List<VideoInfo>) : ExtractResult()
    data class NoneFound(val message: String) : ExtractResult()
}

/**
 * Looks for videos that a page serves openly, digging past the obvious
 * <video>/<source> tags to also catch links buried in JSON-LD metadata,
 * data-* player attributes, and inline <script> blocks. Still does NOT
 * execute JavaScript, reverse-engineer player logic, decrypt signed URLs,
 * or use any platform-private API - if a site only loads its real video
 * via an async JS network call with nothing in the initial HTML, this
 * won't find it, by design.
 */
class VideoLinkExtractor(private val client: OkHttpClient) {

    // Broad set of common single-file video extensions.
    private val extensionPattern =
        "(mp4|webm|mov|mkv|avi|flv|wmv|3gp|m3u8|ts)"

    // Matches a quoted URL (in HTML attributes or inline <script> JSON/JS)
    // ending in one of the extensions above, optionally with a query string.
    private val urlInTextRegex = Pattern.compile(
        "https?://[^\"'\\s<>]+?\\.$extensionPattern(\\?[^\"'\\s<>]*)?",
        Pattern.CASE_INSENSITIVE
    )

    fun extract(pageUrl: String): ExtractResult {
        val html = fetchHtml(pageUrl) ?: return ExtractResult.NoneFound(
            "Unable to fetch the page. Check the URL or your internet connection."
        )
        return extractVideos(pageUrl, html)
    }

    fun extractVideos(pageUrl: String, html: String): ExtractResult {
        val doc = Jsoup.parse(html, pageUrl)
        val found = LinkedHashSet<String>()

        // Standard HTML5 video markup.
        doc.select("video[src]").forEach { found.add(it.attr("abs:src")) }
        doc.select("video source[src]").forEach { found.add(it.attr("abs:src")) }

        // Open Graph / link-rel video hints.
        doc.select(
            "meta[property=og:video], meta[property=og:video:url], " +
                "meta[property=og:video:secure_url], link[rel=video_src]"
        ).forEach {
            val url = it.attr("abs:content").ifBlank { it.attr("abs:href") }
            if (url.isNotBlank()) found.add(url)
        }

        // Player libraries often stash the real source in a data-* attribute.
        doc.select("[data-src], [data-file], [data-video], [data-mp4], [data-hd], [data-source]")
            .forEach { el ->
                for (attrName in listOf("data-src", "data-file", "data-video", "data-mp4", "data-hd", "data-source")) {
                    val url = el.attr("abs:$attrName")
                    if (url.isNotBlank()) found.add(url)
                }
            }

        // Direct <a> links to video files.
        doc.select("a[href~=(?i)\\.$extensionPattern(\\?.*)?$]")
            .forEach { found.add(it.attr("abs:href")) }

        // JSON-LD structured data (schema.org VideoObject)
        doc.select("script[type=application/ld+json]").forEach { script ->
            extractJsonLdVideoUrls(script.data()).forEach { found.add(resolve(pageUrl, it)) }
        }

        // Broad sweep: any quoted video-file URL sitting inside inline <script> blocks
        doc.select("script").forEach { script ->
            val matcher = urlInTextRegex.matcher(script.data())
            while (matcher.find()) {
                found.add(matcher.group())
            }
        }

        val cleaned = found.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) {
            return ExtractResult.NoneFound(
                "No openly-served video file was found on this page, even after checking " +
                    "structured data and inline scripts. If the site loads its real video " +
                    "only through a JavaScript network call after the page renders, this " +
                    "app can't see it without executing that JavaScript, which it " +
                    "intentionally doesn't do."
            )
        }

        // Probe every candidate and rank by file size, largest first.
        val videos = cleaned.mapNotNull { url -> probeVideo(pageUrl, url) }
            .sortedByDescending { it.sizeBytes ?: -1L }

        return if (videos.isEmpty()) {
            ExtractResult.NoneFound("Found video links, but none were publicly downloadable.")
        } else {
            ExtractResult.Found(videos)
        }
    }

    private fun extractJsonLdVideoUrls(json: String): List<String> {
        val keys = listOf("contentUrl", "embedUrl")
        val results = mutableListOf<String>()
        for (key in keys) {
            val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = pattern.matcher(json)
            while (matcher.find()) {
                results.add(matcher.group(1))
            }
        }
        return results
    }

    private fun resolve(pageUrl: String, maybeRelative: String): String {
        return runCatching { URI(pageUrl).resolve(maybeRelative).toString() }.getOrDefault(maybeRelative)
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
