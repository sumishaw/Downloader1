package com.example.videodownloader.download

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.videodownloader.model.VideoInfo
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val downloader = ChunkedDownloader(client)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val directUrl = intent?.getStringExtra(EXTRA_DIRECT_URL) ?: return START_NOT_STICKY
        val pageUrl = intent.getStringExtra(EXTRA_PAGE_URL).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "video_${System.currentTimeMillis()}.mp4"
        val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, -1L).takeIf { it > 0 }
        val supportsRanges = intent.getBooleanExtra(EXTRA_SUPPORTS_RANGES, false)

        startForeground(NOTIFICATION_ID, buildNotification(fileName, 0))

        scope.launch {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val destination = File(dir, fileName)

            downloader.download(
                url = directUrl,
                destination = destination,
                totalBytes = totalBytes,
                supportsRanges = supportsRanges
            ) { downloaded, total ->
                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                updateNotification(fileName, percent)
            }.onSuccess {
                showCompletionNotification(fileName, success = true)
                stopSelf()
            }.onFailure {
                showCompletionNotification(fileName, success = false)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(fileName: String, progress: Int): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(fileName: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(fileName, progress))
    }

    private fun showCompletionNotification(fileName: String, success: Boolean) {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (success) "Download complete" else "Download failed")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_DIRECT_URL = "extra_direct_url"
        const val EXTRA_PAGE_URL = "extra_page_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_SUPPORTS_RANGES = "extra_supports_ranges"

        private const val CHANNEL_ID = "downloads_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: android.content.Context, video: VideoInfo) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DIRECT_URL, video.directUrl)
                putExtra(EXTRA_PAGE_URL, video.pageUrl)
                putExtra(EXTRA_FILE_NAME, video.suggestedFileName)
                putExtra(EXTRA_TOTAL_BYTES, video.sizeBytes ?: -1L)
                putExtra(EXTRA_SUPPORTS_RANGES, video.supportsRangeRequests)
            }
            context.startForegroundService(intent)
        }
    }
}
