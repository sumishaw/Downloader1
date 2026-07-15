package com.example.videodownloader.model

/**
 * Represents a video file that was found openly served on a page
 * (i.e. reachable via a plain HTTP GET, no auth/cookies/DRM required).
 */
data class VideoInfo(
    val pageUrl: String,
    val directUrl: String,
    val suggestedFileName: String,
    val mimeType: String?,
    val sizeBytes: Long?, // null if unknown until we HEAD/GET it
    val supportsRangeRequests: Boolean
)

enum class DownloadState {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, NOT_ALLOWED
}

data class DownloadProgress(
    val id: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val message: String? = null
)
