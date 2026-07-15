package com.example.videodownloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.download.DownloadService
import com.example.videodownloader.model.VideoInfo
import com.example.videodownloader.network.ExtractResult
import com.example.videodownloader.network.VideoLinkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Found(val videos: List<VideoInfo>) : UiState()
    data class Blocked(val reason: String) : UiState()
    data class NoneFound(val reason: String) : UiState()
    data class DownloadStarted(val fileName: String) : UiState()
}

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val extractor = VideoLinkExtractor(client)

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun checkUrl(pageUrl: String) {
    _state.value = UiState.Loading
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { extractor.extract(pageUrl.trim()) }
        _state.value = when (result) {
            is ExtractResult.Found -> UiState.Found(result.videos)
            is ExtractResult.NoneFound -> UiState.NoneFound(result.reason)
            else -> UiState.NoneFound("Unknown error occurred")
        }
    }
}

    fun startDownload(video: VideoInfo) {
        DownloadService.start(getApplication(), video)
        _state.value = UiState.DownloadStarted(video.suggestedFileName)
    }

    fun reset() {
        _state.value = UiState.Idle
    }
}
