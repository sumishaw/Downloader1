package com.example.videodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.videodownloader.ui.DownloaderScreen
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import com.example.videodownloader.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If a user shared a link into the app from a browser's Share menu.
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""

        setContent {
            VideoDownloaderTheme {
                DownloaderScreen(viewModel = viewModel, initialUrl = sharedUrl)
            }
        }
    }
}
