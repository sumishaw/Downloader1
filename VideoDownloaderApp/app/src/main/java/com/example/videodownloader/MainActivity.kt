package com.example.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.videodownloader.ui.DownloaderScreen
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import com.example.videodownloader.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* If denied, downloads on Android 9 and below will fail to save - handled gracefully by DownloadService's success/failure reporting. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only Android 9 (API 28) and below need this - newer versions use
        // MediaStore, which doesn't require a runtime storage permission.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

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
