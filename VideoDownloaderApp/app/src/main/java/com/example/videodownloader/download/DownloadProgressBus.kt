package com.example.videodownloader.download

import com.example.videodownloader.model.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple in-process pub/sub so the foreground DownloadService can report
 * live progress (file size, bytes downloaded, completion) to the UI layer.
 * Safe without a bound service or broadcast receiver since both run in the
 * same app process.
 */
object DownloadProgressBus {
    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress

    fun update(value: DownloadProgress) {
        _progress.value = value
    }

    fun clear() {
        _progress.value = null
    }
}
