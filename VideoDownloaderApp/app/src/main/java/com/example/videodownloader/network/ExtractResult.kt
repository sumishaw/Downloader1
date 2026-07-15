package com.example.videodownloader.network

import com.example.videodownloader.model.VideoInfo

/**
 * Sealed class representing the result of extracting video links from a page.
 */
sealed class ExtractResult {
    /**
     * Successfully found one or more videos.
     */
    data class Found(val videos: List<VideoInfo>) : ExtractResult()

    /**
     * Page loaded successfully but no videos were found.
     */
    data class NoneFound(val message: String) : ExtractResult()

    /**
     * Page exists but access is restricted or blocked.
     */
    data class Blocked(val reason: String) : ExtractResult()

    /**
     * An error occurred while trying to extract videos.
     */
    data class Error(val message: String) : ExtractResult()
}