package com.oriol.letsstudy.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface LoopbackOAuthCallbackServer {
    val redirectUri: String
    suspend fun awaitCallback(): String
    fun close()
}

class AndroidLoopbackOAuthCallbackServer : LoopbackOAuthCallbackServer {
    private val serverSocket = ServerSocket().apply {
        reuseAddress = false
        bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_ADDRESS), 0), 1)
        soTimeout = CALLBACK_TIMEOUT_MILLIS
    }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "letsstudy-oauth-callback").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val awaiting = AtomicBoolean(false)
    override val redirectUri: String = "http://$LOOPBACK_ADDRESS:${serverSocket.localPort}$CALLBACK_PATH"

    override suspend fun awaitCallback(): String = suspendCancellableCoroutine { continuation ->
        if (!awaiting.compareAndSet(false, true)) {
            continuation.resumeWithException(IllegalStateException("The callback can only be awaited once."))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { close() }
        executor.execute {
            try {
                while (!closed.get() && continuation.isActive) {
                    val accepted = serverSocket.accept()
                    val callback = accepted.use(::readRequest)
                    if (callback != null) {
                        if (continuation.isActive) continuation.resume(callback)
                        close()
                        return@execute
                    }
                }
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("The authorization callback was closed."))
                }
            } catch (error: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("The authorization callback could not be received."))
                }
            }
        }
    }

    private fun readRequest(socket: Socket): String? {
        socket.soTimeout = CLIENT_TIMEOUT_MILLIS
        if (socket.inetAddress?.isLoopbackAddress != true) {
            respond(socket, 403, "Request rejected")
            return null
        }

        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readBoundedLine(input, MAX_REQUEST_LINE_BYTES)
        if (requestLine == null) {
            respond(socket, 400, "Request rejected")
            return null
        }
        val requestParts = requestLine.split(' ', limit = 3)
        if (requestParts.size != 3 || requestParts[0] != "GET" || requestParts[2] != "HTTP/1.1") {
            respond(socket, 400, "Request rejected")
            return null
        }

        val headers = mutableMapOf<String, String>()
        var consumedBytes = requestLine.length
        while (true) {
            val line = readBoundedLine(input, MAX_HEADER_LINE_BYTES)
            if (line == null) {
                respond(socket, 400, "Request rejected")
                return null
            }
            consumedBytes += line.length
            if (consumedBytes > MAX_HEADER_BYTES) {
                respond(socket, 431, "Request rejected")
                return null
            }
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) {
                respond(socket, 400, "Request rejected")
                return null
            }
            val name = line.substring(0, separator).trim().lowercase()
            if (headers.putIfAbsent(name, line.substring(separator + 1).trim()) != null) {
                respond(socket, 400, "Request rejected")
                return null
            }
        }

        val host = headers["host"]
        val requestTarget = requestParts[1]
        if (host != "127.0.0.1:${serverSocket.localPort}" ||
            requestTarget.substringBefore('?') != CALLBACK_PATH ||
            !requestTarget.startsWith("$CALLBACK_PATH?")
        ) {
            respond(socket, 404, "Authorization callback not found")
            return null
        }

        val callback = "$redirectUri?${requestTarget.substringAfter('?')}"
        respond(socket, 200, "You can return to Let’sStudy.")
        return callback
    }

    private fun readBoundedLine(input: BufferedInputStream, maxBytes: Int): String? {
        val bytes = ArrayList<Byte>()
        while (bytes.size <= maxBytes) {
            val next = input.read()
            if (next == -1) return null
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            bytes.add(next.toByte())
        }
        return null
    }

    private fun respond(socket: Socket, status: Int, message: String) {
        val body = "<!doctype html><html><body><p>$message</p></body></html>".toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            431 -> "Request Header Fields Too Large"
            else -> "Bad Request"
        }
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            BufferedOutputStream(socket.getOutputStream()).use { output ->
                output.write(headers.toByteArray(StandardCharsets.US_ASCII))
                output.write(body)
                output.flush()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }

    companion object {
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
        private const val CALLBACK_PATH = "/auth/callback"
        private const val CALLBACK_TIMEOUT_MILLIS = 180_000
        private const val CLIENT_TIMEOUT_MILLIS = 10_000
        private const val MAX_REQUEST_LINE_BYTES = 4_096
        private const val MAX_HEADER_LINE_BYTES = 2_048
        private const val MAX_HEADER_BYTES = 8_192
    }
}
