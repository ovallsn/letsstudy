package com.oriol.letsstudy.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class ModelFileDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive" }
    }

    suspend fun download(
        url: String,
        partialFile: File,
        targetFile: File,
        expectedSize: Long,
        expectedSha256: String,
        availableBytes: () -> Long = { partialFile.parentFile?.usableSpace ?: 0L },
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ) = withContext(ioDispatcher) {
        val source = URI.create(url)
        val isLoopbackFixture = source.scheme.equals("http", ignoreCase = true) &&
            source.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
        require(source.scheme.equals("https", ignoreCase = true) || isLoopbackFixture) {
            "Model downloads require HTTPS."
        }
        require(expectedSize > 0L) { "expectedSize must be positive" }
        require(expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "expectedSha256 must be a SHA-256 hex digest" }
        require(partialFile.absoluteFile.parentFile == targetFile.absoluteFile.parentFile) {
            "The partial and active model files must share a directory for atomic activation."
        }

        val directory = targetFile.absoluteFile.parentFile
            ?: throw IOException("The model directory is unavailable.")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private model directory.")
        }

        if (targetFile.isFile && targetFile.length() == expectedSize) {
            if (sha256(targetFile, isCancelled) == expectedSha256.lowercase()) {
                partialFile.delete()
                return@withContext
            }
            if (!targetFile.delete()) throw IOException("The existing model file is invalid and could not be removed.")
        }

        if (partialFile.exists() && partialFile.length() > expectedSize) {
            partialFile.delete()
            throw ModelFileIntegrityException("The partial model is larger than the pinned model file.")
        }

        val startingOffset = if (partialFile.isFile) partialFile.length() else 0L
        val remainingBytes = expectedSize - startingOffset
        val freeBeforeDownload = availableBytes()
        val requiredBeforeDownload = remainingBytes + MINIMUM_FREE_SPACE_RESERVE_BYTES
        if (freeBeforeDownload < requiredBeforeDownload) {
            throw ModelInsufficientSpaceException(requiredBeforeDownload, freeBeforeDownload)
        }

        coroutineContext.ensureActive()
        if (isCancelled()) throw CancellationException("Model download cancelled.")

        if (startingOffset < expectedSize) {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "LetsStudy/0.1 (on-device model download)")
                .get()
            if (startingOffset > 0L) requestBuilder.header("Range", "bytes=$startingOffset-")

            val call = client.newCall(requestBuilder.build())
            awaitResponse(call).use { response ->
                if (!response.isSuccessful) throw IOException("Model server returned HTTP ${response.code}.")
                val body = response.body ?: throw IOException("Model server returned an empty response.")
                val append: Boolean
                val transferStart: Long
                when {
                    response.code == 200 -> {
                        // Some mirrors ignore Range. Restart cleanly instead of appending a full file.
                        append = false
                        transferStart = 0L
                    }
                    response.code == 206 -> {
                        validateContentRange(response.header("Content-Range"), startingOffset, expectedSize)
                        append = startingOffset > 0L
                        transferStart = startingOffset
                    }
                    else -> throw IOException("Model server returned an unsupported HTTP ${response.code} response.")
                }

                if (transferStart == 0L && startingOffset > 0L) {
                    val freeAfterDiscardingPartial = availableBytes() + startingOffset
                    val requiredForRestart = expectedSize + MINIMUM_FREE_SPACE_RESERVE_BYTES
                    if (freeAfterDiscardingPartial < requiredForRestart) {
                        throw ModelInsufficientSpaceException(requiredForRestart, freeAfterDiscardingPartial)
                    }
                }
                if (transferStart == 0L && partialFile.exists() && !partialFile.delete()) {
                    throw IOException("Could not restart the partial model download.")
                }
                if (transferStart == 0L && availableBytes() < expectedSize + MINIMUM_FREE_SPACE_RESERVE_BYTES) {
                    throw ModelInsufficientSpaceException(expectedSize + MINIMUM_FREE_SPACE_RESERVE_BYTES, availableBytes())
                }
                if (!partialFile.exists() && !partialFile.createNewFile()) {
                    throw IOException("Could not create the partial model file.")
                }

                var downloaded = transferStart
                onProgress(downloaded, expectedSize)
                FileOutputStream(partialFile, append).use { output ->
                    val input = body.byteStream()
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        coroutineContext.ensureActive()
                        if (isCancelled()) throw CancellationException("Model download cancelled.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded > expectedSize) {
                            throw ModelFileIntegrityException("The downloaded model is larger than expected.")
                        }
                        onProgress(downloaded, expectedSize)
                    }
                    output.fd.sync()
                }
            }
        }

        coroutineContext.ensureActive()
        if (isCancelled()) throw CancellationException("Model download cancelled.")
        if (!partialFile.isFile || partialFile.length() != expectedSize) {
            throw IOException("The model download ended before all bytes arrived; retry to resume it.")
        }
        if (sha256(partialFile, isCancelled) != expectedSha256.lowercase()) {
            partialFile.delete()
            throw ModelFileIntegrityException("The downloaded model did not match its published SHA-256 checksum.")
        }

        activateAtomically(partialFile, targetFile)
        onProgress(expectedSize, expectedSize)
    }

    private fun validateContentRange(header: String?, offset: Long, expectedSize: Long) {
        val match = CONTENT_RANGE.matchEntire(header.orEmpty())
            ?: throw IOException("Model server did not return a valid Content-Range header.")
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        val total = match.groupValues[3].toLongOrNull()
        if (start != offset || end == null || end < start || total != expectedSize) {
            throw IOException("Model server returned an unexpected byte range.")
        }
    }

    private fun sha256(file: File, isCancelled: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(bufferSize)
            while (true) {
                if (Thread.currentThread().isInterrupted || isCancelled()) {
                    throw CancellationException("Model verification cancelled.")
                }
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun activateAtomically(partialFile: File, targetFile: File) {
        try {
            java.nio.file.Files.move(
                partialFile.toPath(),
                targetFile.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
            if (!partialFile.renameTo(targetFile)) {
                throw IOException("Could not activate the verified model file.", unsupported)
            }
        }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.tryResumeValue(response)
            }
        })
    }

    private fun kotlinx.coroutines.CancellableContinuation<Response>.tryResumeValue(response: Response) {
        if (!isActive) {
            response.close()
            return
        }
        resume(response) { _, value, _ -> value.close() }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        const val MINIMUM_FREE_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

open class ModelDownloadException(message: String) : IOException(message)

class ModelInsufficientSpaceException(val requiredBytes: Long, val availableBytes: Long) :
    ModelDownloadException("Not enough free storage to download the study model.")

class ModelFileIntegrityException(message: String) : ModelDownloadException(message)
