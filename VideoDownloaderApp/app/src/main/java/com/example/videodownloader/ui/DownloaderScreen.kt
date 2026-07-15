package com.example.videodownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.model.VideoInfo
import com.example.videodownloader.viewmodel.DownloadViewModel
import com.example.videodownloader.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(viewModel: DownloadViewModel, initialUrl: String = "") {
    var url by remember { mutableStateOf(initialUrl) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Video Downloader") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "Paste a page link. This app only downloads video that a site " +
                    "openly serves as a direct file - no paywalled, DRM-protected, or " +
                    "login-only content.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Video page URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { if (url.isNotBlank()) viewModel.checkUrl(url) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is UiState.Loading
            ) {
                Text("Check for downloadable video")
            }

            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is UiState.Idle -> {}
                is UiState.Loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Blocked -> StatusCard(title = "Not downloadable", message = s.reason)
                is UiState.NoneFound -> StatusCard(title = "No video found", message = s.reason)
                is UiState.DownloadStarted -> StatusCard(
                    title = "Download started",
                    message = "Saving \"${s.fileName}\" - check the notification for progress."
                )
                is UiState.Found -> {
                    Text("Found ${s.videos.size} downloadable video(s):", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.videos) { video ->
                            VideoResultCard(video) { viewModel.startDownload(video) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoResultCard(video: VideoInfo, onDownload: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(video.suggestedFileName, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            video.mimeType?.let { Text("Type: $it", style = MaterialTheme.typography.bodySmall) }
            video.sizeBytes?.let {
                Text("Size: ${it / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (video.supportsRangeRequests) "Fast parallel download supported"
                else "Standard single-stream download",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Download")
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, message: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
