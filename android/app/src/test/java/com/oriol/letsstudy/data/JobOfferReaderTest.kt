package com.oriol.letsstudy.data

import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobOfferReaderTest {
    private val heldCertificate = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .build()
    private val serverCertificates = HandshakeCertificates.Builder()
        .heldCertificate(heldCertificate)
        .build()
    private val clientCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(heldCertificate.certificate)
        .build()
    private val server = MockWebServer().apply {
        useHttps(serverCertificates.sslSocketFactory(), false)
        start()
    }

    @After
    fun closeServer() {
        server.shutdown()
    }

    @Test
    fun prefersJobPostingStructuredContentOverNavigationAndMarketingText() {
        val html = """
            <html><head><title>Careers at Example</title>
            <script type="application/ld+json">
              {"@context":"https://schema.org","@type":"JobPosting","title":"Support Engineer",
               "description":"<p>Handle customer incidents and diagnose network connectivity problems.</p>",
               "qualifications":"Two years of Linux support experience."}
            </script></head>
            <body><nav>Home Products Pricing Careers</nav><main><h1>Join our company</h1>
              <p>We make work better for everyone with friendly support and benefits.</p></main></body></html>
        """.trimIndent()

        val result = JobOfferReader().parseHtml(html, "https://example.com/careers/support-engineer")

        assertTrue(result.readable)
        assertEquals("Support Engineer", result.title)
        assertTrue(result.extractedText.contains("diagnose network connectivity problems"))
        assertFalse(result.extractedText.contains("Home Products Pricing"))
    }

    @Test
    fun usesMainVisibleContentWhenThereIsNoStructuredJobPosting() {
        val html = """
            <html><head><title>Cloud Support Engineer</title></head>
            <body><nav>Home About Careers Products</nav><main>
              <h1>Cloud Support Engineer</h1>
              <p>Responsibilities include helping customers resolve cloud service incidents.</p>
              <p>Requirements include Linux, networking, and clear written communication.</p>
              <p>Apply with a résumé and a short note describing your experience.</p>
            </main><footer>Copyright Example</footer></body></html>
        """.trimIndent()

        val result = JobOfferReader().parseHtml(html, "https://example.com/jobs/cloud-support")

        assertTrue(result.readable)
        assertEquals("Cloud Support Engineer", result.title)
        assertTrue(result.extractedText.contains("resolve cloud service incidents"))
        assertFalse(result.extractedText.contains("Copyright Example"))
    }

    @Test
    fun rejectsNonHttpsAndPrivateLiteralAddressesWithoutMakingARequest() = kotlinx.coroutines.runBlocking {
        val reader = JobOfferReader()

        val http = reader.read("http://jobs.example.com/support")
        val loopback = reader.read("https://127.0.0.1/private")

        assertFalse(http.readable)
        assertFalse(loopback.readable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun capsRedirectsAndRejectsOversizedPages() = kotlinx.coroutines.runBlocking {
        repeat(6) { index ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/redirect-${index + 1}"),
            )
        }
        val redirectResult = readerForLocalFixture().read(server.url("/start").toString())
        assertFalse(redirectResult.readable)
        assertEquals(6, server.requestCount)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("x".repeat(JobOfferReader.MAX_RESPONSE_BYTES + 1)),
        )
        val oversizedResult = readerForLocalFixture().read(server.url("/large").toString())
        assertFalse(oversizedResult.readable)
    }

    private fun readerForLocalFixture() = JobOfferReader(
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build(),
        dns = Dns.SYSTEM,
        isAddressPublic = { true },
    )
}
