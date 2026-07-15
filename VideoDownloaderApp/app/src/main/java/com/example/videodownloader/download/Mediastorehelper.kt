package com.example.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Moves a finished download out of the app's private staging area and into
 * the phone's public Movies collection, so it shows up in the Gallery,
 * Files app, and any other file manager - not just inside this app.
 *
 * On Android 10+ (API 29+) this goes through MediaStore, which is the only
 * supported way to write into shared storage. On older versions it falls
 * back to writing directly into the public Movies directory and triggers a
 * media scan so it appears immediately.
 */
object MediaStoreHelper {

    private const val SUBFOLDER = "VideoDownloader"

    fun publishVideo(context: Context, sourceFile: File, displayName: String, mimeType: String?): Boolean {
        return try {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, sourceFile, displayName, mimeType)
            } else {
                publishLegacy(context, sourceFile, displayName)
            }
            if (success) sourceFile.delete()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun publishViaMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String?
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType ?: "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        val copyOk = resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            true
        } ?: false

        if (!copyOk) return false

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return true
    }

    private fun publishLegacy(context: Context, sourceFile: File, displayName: String): Boolean {
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            SUBFOLDER
        )
        if (!moviesDir.exists() && !moviesDir.mkdirs()) return false

        val destFile = File(moviesDir, displayName)
        sourceFile.copyTo(destFile, overwrite = true)

        // Without this, older Android versions may not show the file in
        // Gallery/Files until the next full media scan (e.g. a reboot).
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
        return true
    }
}
