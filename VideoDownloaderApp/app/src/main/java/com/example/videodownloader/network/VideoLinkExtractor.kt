package com.example.videodownloader.network

import com.example.videodownloader.model.VideoInfo
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

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
 *
 * Note: This approach does NOT execute JavaScript. Pages that load videos
 * dynamically via React, Vue, fetch(), AJAX, etc. will not have their
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
            val request = okhttp3.Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return ExtractResult.Error("Failed to fetch page: ${response.code}")
            }

            val html = response.body?.string()
                ?: return ExtractResult.NoneFound("Empty response body")

            val doc = Jsoup.parse(html, pageUrl)
            val videos = mutableSetOf<String>() // Use set to avoid duplicates

            // 1. Extract from <video> and <source> tags
            extractFromVideoTags(doc, pageUrl, videos)

            // 2. Extract from Open Graph meta tags
            extractFromOpenGraph(doc, pageUrl, videos)

            // 3. Extract from JSON-LD structured data
            extractFromJsonLd(doc, pageUrl, videos)

            // 4. Extract from data attributes and other meta tags
            extractFromDataAttributes(doc, pageUrl, videos)

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

        // Look for video URLs in script tags (inline JS with URL strings)
        doc.select("script:not([type])").forEach { script ->
            val scriptContent = script.data()
            // Simple pattern to find URLs in inline scripts
            val urlPattern =
                """(?:https?://|/)(?:[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+\.(?:mp4|webm|ogg|mov|mkv))""".toRegex()
            urlPattern.findAll(scriptContent).forEach { match ->
                val url = match.value
                resolveUrl(url, baseUrl)?.let { videos.add(it) }
            }
        }
    }

    private fun tryCreateVideoInfo(url: String, pageUrl: String): VideoInfo? {
        return try {
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
            val url = URL(baseUrl).toURI().resolve(urlStr).toURL()
            url.toString()
        } catch (e: Exception) {
            // Try parsing as absolute URL
            try {
                URL(urlStr).toString()
            } catch {
                null
            }
        }
    }

    private fun extractFileName(url: String): String {
        val fileName = url.substringAfterLast("/")
            .substringBefore("?")
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
            else -> null
        }
    }
}
