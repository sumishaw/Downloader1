package com.example.videodownloader.download

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads a single file, splitting it into N byte-range chunks fetched
 * in parallel when the server supports range requests (Accept-Ranges: bytes).
 * Falls back to a single-stream download otherwise.
 */
class ChunkedDownloader(
    private val client: OkHttpClient,
    private val threadCount: Int = 4
) {

    suspend fun download(
        url: String,
        destination: File,
        totalBytes: Long?,
        supportsRanges: Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!supportsRanges || totalBytes == null || totalBytes <= 0) {
                singleStreamDownload(url, destination, onProgress)
            } else {
                parallelChunkDownload(url, destination, totalBytes, onProgress)
            }
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun singleStreamDownload(
        url: String,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            var downloaded = 0L
            destination.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }
    }

    private suspend fun parallelChunkDownload(
        url: String,
        destination: File,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Pre-allocate the file to full size so chunks can write at their own offsets.
        RandomAccessFile(destination, "rw").use { it.setLength(totalBytes) }

        val chunkSize = totalBytes / threadCount
        val downloadedTotal = AtomicLong(0)

        val ranges = (0 until threadCount).map { i ->
            val start = i * chunkSize
            val end = if (i == threadCount - 1) totalBytes - 1 else (start + chunkSize - 1)
            start to end
        }

        coroutineScope {
            ranges.map { (start, end) ->
                async {
                    downloadRange(url, destination, start, end) { chunkBytesJustRead ->
                        val newTotal = downloadedTotal.addAndGet(chunkBytesJustRead)
                        onProgress(newTotal, totalBytes)
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadRange(
        url: String,
        destination: File,
        start: Long,
        end: Long,
        onBytesRead: (Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Empty response body for range $start-$end")
            RandomAccessFile(destination, "rw").use { raf ->
                raf.seek(start)
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        onBytesRead(read.toLong())
                    }
                }
            }
        }
    }
}
