package com.example.videodownloader.model

/**
 * Represents a downloadable video extracted from a webpage.
 *
 * @param directUrl The direct URL to the video file
 * @param pageUrl The URL of the page where the video was found
 * @param suggestedFileName A suggested filename for saving the video
 * @param mimeType The MIME type of the video (e.g., "video/mp4")
 * @param sizeBytes The size of the video in bytes, or null if unknown
 * @param supportsRangeRequests Whether the server supports HTTP range requests for resumable downloads
 */
data class VideoInfo(
    val directUrl: String,
    val pageUrl: String,
    val suggestedFileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val supportsRangeRequests: Boolean = false
)