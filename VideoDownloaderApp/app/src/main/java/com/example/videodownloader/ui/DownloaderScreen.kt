package com.example.videodownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.model.DownloadProgress
import com.example.videodownloader.model.DownloadState
import com.example.videodownloader.model.VideoInfo
import com.example.videodownloader.viewmodel.DownloadViewModel
import com.example.videodownloader.viewmodel.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(viewModel: DownloadViewModel, initialUrl: String = "") {
    var url by remember { mutableStateOf(initialUrl) }
    val state by viewModel.state.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()

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

            // Live download progress - shown whenever a download is queued,
            // in progress, completed, or failed, independent of the
            // check/search state above.
            progress?.let { p ->
                DownloadProgressCard(progress = p, onDismiss = { viewModel.dismissProgress() })
                Spacer(Modifier.height(16.dp))
            }

            when (val s = state) {
                is UiState.Idle -> {}
                is UiState.Loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Blocked -> StatusCard(title = "Not downloadable", message = s.reason)
                is UiState.NoneFound -> StatusCard(title = "No video found", message = s.reason)
                is UiState.DownloadStarted -> {} // covered by the progress card above
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
private fun DownloadProgressCard(progress: DownloadProgress, onDismiss: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(progress.fileName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (progress.state == DownloadState.COMPLETED || progress.state == DownloadState.FAILED) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            when (progress.state) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                    val hasTotal = progress.totalBytes > 0
                    Text(
                        if (hasTotal)
                            "${formatBytes(progress.bytesDownloaded)} of ${formatBytes(progress.totalBytes)}"
                        else
                            "${formatBytes(progress.bytesDownloaded)} downloaded (total size unknown)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    if (hasTotal) {
                        val fraction = (progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                DownloadState.COMPLETED -> {
                    Text(
                        "Download complete \u2013 ${formatBytes(progress.totalBytes)} saved to " +
                            "Movies/VideoDownloader (visible in your Gallery/Files app)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                DownloadState.FAILED -> {
                    Text(
                        "Download failed" + (progress.message?.let { ": $it" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                DownloadState.PAUSED, DownloadState.NOT_ALLOWED -> {
                    Text(progress.message ?: progress.state.name, style = MaterialTheme.typography.bodySmall)
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
                Text("Size: ${formatBytes(it)}", style = MaterialTheme.typography.bodySmall)
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024)
    else String.format(Locale.US, "%.1f MB", mb)
}
