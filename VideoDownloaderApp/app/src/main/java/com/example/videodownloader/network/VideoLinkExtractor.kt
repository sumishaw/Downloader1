package com.example.videodownloader.network

import com.example.videodownloader.model.VideoInfo
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.net.URLDecoder

/**
 * Extracts video links from webpages by parsing HTML.
 *
 * This extractor uses OkHttp to fetch pages and Jsoup to parse HTML.
 * It searches for videos in:
 * - <video> and <source> tags
 * - Open Graph meta tags (og:video, og:video:url, og:video:secure_url)
 * - JSON-LD structured data
 * - Data attributes
 * - Inline script URLs
 * - JavaScript variables and object patterns
 * - Common video platform patterns
 *
 * Note: This approach does NOT execute JavaScript. Pages that load videos
 * dynamically via React, Vue, fetch(), AJAX, etc. may not have their
 * videos extracted. For those sites, a headless browser solution is needed.
 */
class VideoLinkExtractor(private val client: OkHttpClient) {

    /**
     * Extracts video links from the given page URL.
     *
     * @param pageUrl The URL of the page to extract videos from
     * @return An ExtractResult containing the videos found, or an error/no-videos state
     */
    suspend fun extract(pageUrl: String): ExtractResult {
        return try {
            // First, check if the domain is allowed by download policy
            if (!DownloadPolicy.isDomainAllowed(pageUrl)) {
                return ExtractResult.Blocked(
                    "This site's Terms of Service do not allow downloading its videos, " +
                        "so this app won't extract from it."
                )
            }

            val request = okhttp3.Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Referer", pageUrl)
                .header("DNT", "1")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return ExtractResult.Error("Failed to fetch page: ${response.code}")
            }

            val html = response.body?.string()
                ?: return ExtractResult.NoneFound("Empty response body")

            // Check if the response looks like it's DRM-protected or requires authentication
            if (DownloadPolicy.looksLikeDrmOrAuthProtected(response.header("Content-Type"), html)) {
                return ExtractResult.Blocked("This video appears to be DRM-protected or requires login.")
            }

            val doc = Jsoup.parse(html, pageUrl)
            val videos = mutableSetOf<String>() // Use set to avoid duplicates

            // 1. Extract from <video> and <source> tags
            extractFromVideoTags(doc, pageUrl, videos)

            // 2. Extract from Open Graph meta tags
            extractFromOpenGraph(doc, pageUrl, videos)

            // 3. Extract from JSON-LD structured data
            extractFromJsonLd(doc, pageUrl, videos)

            // 4. Extract from data attributes
            extractFromDataAttributes(doc, pageUrl, videos)

            // 5. Extract video URLs from JavaScript code (inline scripts)
            extractFromJavaScript(doc, pageUrl, videos)

            // 6. Extract from common video platform patterns
            extractFromCommonPatterns(doc, pageUrl, videos)

            // 7. Extract from meta tags
            extractFromMetaTags(doc, pageUrl, videos)

            if (videos.isEmpty()) {
                return ExtractResult.NoneFound("No video links found on this page")
            }

            // Convert URLs to VideoInfo objects with metadata
            val videoInfos = videos.mapNotNull { url ->
                tryCreateVideoInfo(url, pageUrl)
            }

            return if (videoInfos.isNotEmpty()) {
                ExtractResult.Found(videoInfos)
            } else {
                ExtractResult.NoneFound("Found video URLs but could not parse them")
            }

        } catch (e: Exception) {
            ExtractResult.Error(e.message ?: "Unknown error occurred during extraction")
        }
    }

    private fun extractFromVideoTags(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // <video><source src="..."></video>
        doc.select("video source").forEach { element ->
            element.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                resolveUrl(src, baseUrl)?.let { videos.add(it) }
            }
        }

        // Direct <video src="..."></video>
        doc.select("video").forEach { element ->
            element.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                resolveUrl(src, baseUrl)?.let { videos.add(it) }
            }
        }
    }

    private fun extractFromOpenGraph(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // og:video, og:video:url, og:video:secure_url
        listOf("og:video", "og:video:url", "og:video:secure_url").forEach { property ->
            doc.selectFirst("meta[property=$property]")?.attr("content")
                ?.takeIf { it.isNotEmpty() }?.let { url ->
                    resolveUrl(url, baseUrl)?.let { videos.add(it) }
                }
        }
    }

    private fun extractFromJsonLd(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        doc.select("script[type=application/ld\\+json]").forEach { script ->
            try {
                val jsonText = script.data()
                // Look for "contentUrl", "url", or "videoUrl" in JSON-LD
                listOf("\"contentUrl\"", "\"url\"", "\"videoUrl\"").forEach { key ->
                    val pattern = """$key\s*:\s*\"([^\"]+)""".toRegex()
                    pattern.findAll(jsonText).forEach { match ->
                        val url = match.groupValues[1]
                        resolveUrl(url, baseUrl)?.let { videos.add(it) }
                    }
                }
            } catch (e: Exception) {
                // Silently skip malformed JSON-LD
            }
        }
    }

    private fun extractFromDataAttributes(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // data-video, data-src attributes
        doc.select("[data-video], [data-src]").forEach { element ->
            sequenceOf(
                element.attr("data-video"),
                element.attr("data-src")
            ).filter { it.isNotEmpty() && (it.startsWith("http") || it.startsWith("/")) }
                .forEach { src ->
                    resolveUrl(src, baseUrl)?.let { videos.add(it) }
                }
        }
    }

    private fun extractFromJavaScript(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // Extract URLs from all script tags
        doc.select("script").forEach { script ->
            val scriptContent = script.data()
            if (scriptContent.isEmpty()) return@forEach

            // Common video URL patterns in JavaScript
            val patterns = listOf(
                // Direct video file URLs (mp4, webm, etc.)
                """(?:https?:|)//[^"'\s<>]+\.(?:mp4|webm|ogg|mov|mkv|avi|flv|m3u8)(?:\?[^"'\s<>]*)?""".toRegex(),
                // src= or url= patterns
                """(?:src|url)\s*[:=]\s*["']([^"']+\.(?:mp4|webm|ogg|mov|mkv|avi|flv|m3u8)[^"']*)["']""".toRegex(),
                // data-src or data-video patterns  
                """data-(?:src|video)\s*=\s*["']([^"']+)["']""".toRegex(),
                // "url": "..." JSON patterns
                """"(?:url|src|videoUrl|video_url|media_url)"\s*[:=]\s*"([^"]+\.(?:mp4|webm|ogg|mov|mkv|avi|flv|m3u8)[^"]*)""".toRegex(),
                // 'url': '...' single quote JSON patterns
                """'(?:url|src|videoUrl|video_url|media_url)'\s*[:=]\s*'([^']+\.(?:mp4|webm|ogg|mov|mkv|avi|flv|m3u8)[^']*)""".toRegex(),
                // HLS/DASH streams
                """(?:https?:|)//[^"'\s<>]+\.(?:m3u8|mpd)(?:\?[^"'\s<>]*)?""".toRegex()
            )

            patterns.forEach { pattern ->
                pattern.findAll(scriptContent).forEach { match ->
                    val url = if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) {
                        match.groupValues[1]
                    } else {
                        match.value
                    }
                    if (url.isNotEmpty() && isValidVideoUrl(url)) {
                        resolveUrl(url, baseUrl)?.let { videos.add(it) }
                    }
                }
            }
        }
    }

    private fun extractFromCommonPatterns(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // Look for common video player patterns
        doc.select("iframe, [class*='player'], [class*='video'], [class*='embed']").forEach { element ->
            // Extract src from iframes
            element.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                if (isVideoPlayerUrl(src)) {
                    resolveUrl(src, baseUrl)?.let { videos.add(it) }
                }
            }

            // Extract data-* attributes
            element.attributes().forEach { attr ->
                val attrValue = attr.value
                if (attrValue.contains(Regex("""\.(mp4|webm|ogg|mov|mkv|avi|flv|m3u8)"""))) {
                    resolveUrl(attrValue, baseUrl)?.let { videos.add(it) }
                }
            }
        }

        // Extract from picture sources
        doc.select("picture source").forEach { element ->
            element.attr("src").takeIf { it.isNotEmpty() }?.let { src ->
                if (src.contains(Regex("""\.(mp4|webm)"""))) {
                    resolveUrl(src, baseUrl)?.let { videos.add(it) }
                }
            }
        }
    }

    private fun extractFromMetaTags(doc: Document, baseUrl: String, videos: MutableSet<String>) {
        // Extract from various meta tags that might contain video URLs
        doc.select("meta[name=video], meta[name=video-url], meta[property=video], meta[property=video-url]")
            .forEach { element ->
                element.attr("content").takeIf { it.isNotEmpty() }?.let { url ->
                    resolveUrl(url, baseUrl)?.let { videos.add(it) }
                }
            }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        val videoExtensions = listOf("mp4", "webm", "ogg", "mov", "mkv", "avi", "flv", "m3u8", "mpd")
        return videoExtensions.any { url.contains(".$it", ignoreCase = true) }
    }

    private fun isVideoPlayerUrl(url: String): Boolean {
        // Common video platform domains
        val videoPlatforms = listOf(
            "youtube.com", "youtu.be",
            "vimeo.com",
            "dailymotion.com",
            "twitter.com", "x.com",
            "facebook.com",
            "instagram.com",
            "tiktok.com",
            "twitch.tv",
            "rumble.com",
            "bitchute.com"
        )
        return videoPlatforms.any { url.contains(it) }
    }

    private fun tryCreateVideoInfo(url: String, pageUrl: String): VideoInfo? {
        return try {
            // Skip invalid URLs
            if (!isValidVideoUrl(url) && !isVideoPlayerUrl(url)) {
                return null
            }

            val fileName = extractFileName(url)
            val mimeType = guessMimeType(url)

            VideoInfo(
                directUrl = url,
                pageUrl = pageUrl,
                suggestedFileName = fileName,
                mimeType = mimeType,
                sizeBytes = null,
                supportsRangeRequests = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveUrl(urlStr: String, baseUrl: String): String? {
        return try {
            // Handle URL encoding/decoding
            val decodedUrl = try {
                URLDecoder.decode(urlStr, "UTF-8")
            } catch (e: Exception) {
                urlStr
            }

            // Try to resolve relative URLs
            val url = URL(baseUrl).toURI().resolve(decodedUrl).toURL()
            url.toString()
        } catch (e: Exception) {
            // Try parsing as absolute URL
            try {
                URL(urlStr).toString()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun extractFileName(url: String): String {
        val cleanUrl = url.substringBefore("?")
            .substringBefore("#")

        val fileName = cleanUrl.substringAfterLast("/")
            .takeIf { it.isNotEmpty() }
            ?: "video_${System.currentTimeMillis()}"

        return if (fileName.contains(".")) {
            fileName
        } else {
            "$fileName.mp4"
        }
    }

    private fun guessMimeType(url: String): String? {
        return when {
            url.contains(".mp4", ignoreCase = true) -> "video/mp4"
            url.contains(".webm", ignoreCase = true) -> "video/webm"
            url.contains(".ogg", ignoreCase = true) -> "video/ogg"
            url.contains(".mov", ignoreCase = true) -> "video/quicktime"
            url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            url.contains(".avi", ignoreCase = true) -> "video/x-msvideo"
            url.contains(".flv", ignoreCase = true) -> "video/x-flv"
            url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
            else -> null
        }
    }
}
