package com.example.videodownloader.model

/**
 * Represents the current state of a download operation.
 */
data class DownloadProgress(
    val id: String,
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val message: String? = null
)

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}