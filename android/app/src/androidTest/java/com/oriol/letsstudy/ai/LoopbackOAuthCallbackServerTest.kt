package com.oriol.letsstudy.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LoopbackOAuthCallbackServerTest {
    @Test
    fun acceptsOneLoopbackCallbackAndRejectsWrongRouteAndHost() = runBlocking {
        val server = AndroidLoopbackOAuthCallbackServer()
        try {
            val redirect = URI(server.redirectUri)
            assertEquals("http", redirect.scheme)
            assertEquals("127.0.0.1", redirect.host)
            assertEquals("/auth/callback", redirect.path)
            assertTrue(redirect.port > 0)

            val callback = async { server.awaitCallback() }
            delay(100)

            val wrongPath = request(server.redirectUri, "/other?code=bad")
            assertEquals(404, wrongPath)

            val wrongHost = request(server.redirectUri, "/auth/callback?code=bad", "localhost:${redirect.port}")
            assertEquals(404, wrongHost)

            val requestUrl = "${server.redirectUri}?code=one-time-code&state=one-time-state"
            val acceptedStatus = request(server.redirectUri, "/auth/callback?code=one-time-code&state=one-time-state")
            assertEquals(200, acceptedStatus)
            assertEquals(requestUrl, callback.await())

            val closedPort = ServerSocket(0).use { it.localPort }
            var wrongPortRejected = false
            try {
                Socket().use { it.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), closedPort), 1_000) }
            } catch (_: IOException) {
                wrongPortRejected = true
            }
            assertTrue(wrongPortRejected)
            assertTrue(callback.isCompleted)
        } finally {
            server.close()
        }
    }

    private fun request(redirectUri: String, target: String, host: String? = null): Int {
        val redirect = URI(redirectUri)
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName(redirect.host), redirect.port), 2_000)
            socket.soTimeout = TimeUnit.SECONDS.toMillis(2).toInt()
            val output = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            output.write("GET $target HTTP/1.1\r\n")
            output.write("Host: ${host ?: "127.0.0.1:${redirect.port}"}\r\n")
            output.write("Connection: close\r\n\r\n")
            output.flush()
            socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                .split(' ')[1]
                .toInt()
        }
    }
}
