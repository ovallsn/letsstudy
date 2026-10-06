package com.oriol.letsstudy.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelFileDownloaderTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun closeServer() {
        server.shutdown()
    }

    @Test
    fun resumesFromExistingPartialAndActivatesOnlyVerifiedBytes() = runBlocking {
        val bytes = ByteArray(8_192) { (it % 251).toByte() }
        val partialLength = 2_048
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $partialLength-${bytes.lastIndex}/${bytes.size}")
                .setBody(okio.Buffer().write(bytes, partialLength, bytes.size - partialLength)),
        )
        val directory = Files.createTempDirectory("model-resume-").toFile()
        try {
            val partial = File(directory, "model.part").apply { writeBytes(bytes.copyOfRange(0, partialLength)) }
            val target = File(directory, "model.litertlm")

            ModelFileDownloader(OkHttpClient(), bufferSize = 512).download(
                url = server.url("model").toString(),
                partialFile = partial,
                targetFile = target,
                expectedSize = bytes.size.toLong(),
                expectedSha256 = bytes.sha256(),
                availableBytes = { 1_000_000_000L },
            )

            val actualRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertTrue(actualRequest.getHeader("Range") == "bytes=$partialLength-")
            assertArrayEquals(bytes, target.readBytes())
            assertFalse(partial.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellationKeepsPartialBytesAndDoesNotCreateActiveModel() = runBlocking {
        val bytes = ByteArray(16_384) { 7 }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        val directory = Files.createTempDirectory("model-cancel-").toFile()
        try {
            val partial = File(directory, "model.part")
            val target = File(directory, "model.litertlm")
            var latestProgress = 0L

            try {
                ModelFileDownloader(OkHttpClient(), bufferSize = 256).download(
                    url = server.url("model").toString(),
                    partialFile = partial,
                    targetFile = target,
                    expectedSize = bytes.size.toLong(),
                    expectedSha256 = bytes.sha256(),
                    availableBytes = { 1_000_000_000L },
                    onProgress = { downloaded, _ -> latestProgress = downloaded },
                    isCancelled = { latestProgress >= 256L },
                )
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) {
                // Expected: partial data must remain available for the next attempt.
            }

            assertTrue(partial.exists())
            assertTrue(partial.length() in 1L until bytes.size.toLong())
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun checksumMismatchDeletesPartialAndNeverActivatesModel() = runBlocking {
        val bytes = "wrong payload".toByteArray()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        val directory = Files.createTempDirectory("model-checksum-").toFile()
        try {
            val partial = File(directory, "model.part")
            val target = File(directory, "model.litertlm")

            try {
                ModelFileDownloader(OkHttpClient()).download(
                    url = server.url("model").toString(),
                    partialFile = partial,
                    targetFile = target,
                    expectedSize = bytes.size.toLong(),
                    expectedSha256 = "0".repeat(64),
                    availableBytes = { 1_000_000_000L },
                )
                throw AssertionError("Expected checksum failure")
            } catch (_: ModelFileIntegrityException) {
                // Expected: corrupted bytes are not retained or promoted.
            }

            assertFalse(partial.exists())
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun refusesToStartWhenThereIsNotEnoughFreeSpace() = runBlocking {
        val bytes = "model".toByteArray()
        val directory = Files.createTempDirectory("model-space-").toFile()
        try {
            val partial = File(directory, "model.part")
            val target = File(directory, "model.litertlm")

            try {
                ModelFileDownloader(OkHttpClient()).download(
                    url = server.url("model").toString(),
                    partialFile = partial,
                    targetFile = target,
                    expectedSize = bytes.size.toLong(),
                    expectedSha256 = bytes.sha256(),
                    availableBytes = {
                        bytes.size + ModelFileDownloader.MINIMUM_FREE_SPACE_RESERVE_BYTES - 1L
                    },
                )
                throw AssertionError("Expected insufficient-space failure")
            } catch (_: ModelInsufficientSpaceException) {
                // Expected: reject before making a network request.
            }

            assertTrue(partial.parentFile?.listFiles().orEmpty().isEmpty())
            assertTrue(server.requestCount == 0)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
}
