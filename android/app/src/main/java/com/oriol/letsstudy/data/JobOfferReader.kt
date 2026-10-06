package com.oriol.letsstudy.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class JobOfferSource(
    val canonicalUrl: String,
    val title: String,
    val extractedText: String,
    val readable: Boolean,
    val reason: String? = null,
)

interface JobOfferSourceReader {
    suspend fun read(url: String): JobOfferSource
}

class JobOfferReader internal constructor(
    client: OkHttpClient = defaultClient(),
    dns: Dns? = null,
    private val isAddressPublic: (InetAddress) -> Boolean = ::isPublicAddress,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobOfferSourceReader {
    private val guardedDns = dns ?: object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !isAddressPublic(it) }) {
                throw UnknownHostException("The host is not a public internet address.")
            }
            return addresses
        }
    }
    private val client = client.newBuilder()
        .dns(guardedDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    override suspend fun read(url: String): JobOfferSource = withContext(ioDispatcher) {
        val initial = url.trim().toHttpUrlOrNull()
            ?: return@withContext unreadable(url, "That link is not a valid web address.")
        if (!initial.isPublicHttps()) {
            return@withContext unreadable(url, "For safety, Let’sStudy only reads public HTTPS job pages.")
        }

        try {
            var current = initial
            var redirects = 0
            while (true) {
                if (!isPublic(current.host)) {
                    return@withContext unreadable(current.toString(), "This link points to a private or unavailable address.")
                }
                val request = Request.Builder()
                    .url(current)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/ld+json;q=0.9,*/*;q=0.1")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code in REDIRECT_CODES) {
                        if (redirects >= MAX_REDIRECTS) {
                            return@withContext unreadable(current.toString(), "This page redirects too many times.")
                        }
                        val location = response.header("Location")
                            ?: return@withContext unreadable(current.toString(), "This page sent an invalid redirect.")
                        val next = current.resolve(location)
                            ?: return@withContext unreadable(current.toString(), "This page sent an invalid redirect.")
                        if (!next.isPublicHttps()) {
                            return@withContext unreadable(next.toString(), "This page redirects to a non-public or non-HTTPS address.")
                        }
                        current = next
                        redirects++
                        return@use null
                    }

                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            401, 403 -> "This job page requires sign-in or blocks automated reading. Paste the job description instead."
                            404 -> "This job page could not be found. Check the link or paste the job description."
                            else -> "This job page could not be opened (HTTP ${response.code}). Paste the job description instead."
                        }
                        return@withContext unreadable(current.toString(), message)
                    }

                    val body = response.body ?: return@withContext unreadable(current.toString(), "This page did not return readable content.")
                    if (body.contentLength() > MAX_RESPONSE_BYTES) {
                        return@withContext unreadable(current.toString(), "This page is too large to read. Paste the job description instead.")
                    }
                    val bytes = body.byteStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(BODY_BUFFER_SIZE)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_RESPONSE_BYTES) {
                                throw PageTooLargeException()
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                    return@withContext parseHtml(String(bytes, charset), response.request.url.toString())
                }
                // A redirect response returns null from the use block; follow its validated target.
            }
            @Suppress("UNREACHABLE_CODE")
            unreadable(url, "This page could not be read.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PageTooLargeException) {
            unreadable(url, "This page is too large to read. Paste the job description instead.")
        } catch (_: UnknownHostException) {
            unreadable(url, "This link points to a private or unavailable address.")
        } catch (_: IOException) {
            unreadable(url, "Let’sStudy couldn’t load this page. Check your connection or paste the job description.")
        } catch (_: Exception) {
            unreadable(url, "Let’sStudy couldn’t read this page. Paste the job description instead.")
        }
    }

    fun parseHtml(html: String, finalUrl: String): JobOfferSource {
        val document = Jsoup.parse(html, finalUrl)
        val structuredPosting = findJobPosting(document)
        val title: String
        val text: String
        if (structuredPosting != null) {
            title = structuredPosting.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf(String::isNotBlank)
                ?: document.selectFirst("h1")?.text().orEmpty().ifBlank { document.title() }
            val fields = listOf(
                "description",
                "responsibilities",
                "qualifications",
                "experienceRequirements",
                "educationRequirements",
                "skills",
                "employmentType",
                "jobLocation",
                "hiringOrganization",
            )
            val details = fields.mapNotNull { key ->
                structuredPosting.get(key)?.let(::flattenJsonValue)?.takeIf(String::isNotBlank)
            }
            text = buildString {
                appendLine("Job title: $title")
                details.forEach(::appendLine)
            }.cleanText()
        } else {
            title = document.selectFirst("main h1, [role=main] h1, article h1, h1")?.text()
                ?.takeIf(String::isNotBlank) ?: document.title()
            val main = document.selectFirst("main")
                ?: document.selectFirst("[role=main]")
                ?: document.selectFirst("article")
                ?: document.body()
            val readableMain = main.clone()
            readableMain.select("script,style,nav,header,footer,aside,form,button,[aria-hidden=true]").remove()
            text = buildString {
                if (title.isNotBlank()) appendLine("Page title: $title")
                append(readableMain.text())
            }.cleanText()
        }

        val challengeReason = challengeReason(document)
        if (challengeReason != null) return unreadable(finalUrl, challengeReason)

        val hasStructuredPosting = structuredPosting != null
        val hasJobTerms = JOB_TERMS.findAll(text).count() >= 2
        val hasJobPath = finalUrl.toHttpUrlOrNull()?.encodedPath.orEmpty().contains(JOB_PATH_TERMS)
        if (text.length < MIN_TEXT_CHARS || (!hasStructuredPosting && !hasJobTerms && !hasJobPath)) {
            return unreadable(finalUrl, "This page doesn’t look like a readable job listing. Paste the job description instead.")
        }
        return JobOfferSource(
            canonicalUrl = finalUrl,
            title = title.ifBlank { "Job listing" }.take(MAX_TITLE_CHARS),
            extractedText = text.take(MAX_EXTRACTED_CHARS),
            readable = true,
        )
    }

    private fun findJobPosting(document: Document): JsonObject? {
        document.select("script[type=application/ld+json]").forEach { script ->
            val root = try {
                JsonParser.parseString(script.data().ifBlank { script.html() })
            } catch (_: RuntimeException) {
                return@forEach
            }
            findJobPosting(root)?.let { return it }
        }
        return null
    }

    private fun findJobPosting(value: JsonElement?): JsonObject? = when {
        value == null || value.isJsonNull -> null
        value.isJsonObject -> {
            val objectValue = value.asJsonObject
            val type = objectValue.get("@type")
            val isJob = when {
                type == null || type.isJsonNull -> false
                type.isJsonPrimitive -> type.asString.equals("JobPosting", ignoreCase = true)
                type.isJsonArray -> type.asJsonArray.any { it.isJsonPrimitive && it.asString.equals("JobPosting", ignoreCase = true) }
                else -> false
            }
            if (isJob) objectValue else {
                var found: JsonObject? = null
                for ((_, child) in objectValue.entrySet()) {
                    found = findJobPosting(child)
                    if (found != null) break
                }
                found
            }
        }
        value.isJsonArray -> {
            var found: JsonObject? = null
            for (child in value.asJsonArray) {
                found = findJobPosting(child)
                if (found != null) break
            }
            found
        }
        else -> null
    }

    private fun flattenJsonValue(value: JsonElement?): String = when {
        value == null || value.isJsonNull -> ""
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> Jsoup.parse(value.asString).text()
        value.isJsonObject -> value.asJsonObject.entrySet().asSequence()
            .mapNotNull { (_, child) -> flattenJsonValue(child).takeIf(String::isNotBlank) }
            .joinToString(" ")
        value.isJsonArray -> value.asJsonArray.asSequence()
            .mapNotNull { child -> flattenJsonValue(child).takeIf(String::isNotBlank) }
            .joinToString(" ")
        value.isJsonPrimitive -> value.asString
        else -> ""
    }

    private fun challengeReason(document: Document): String? {
        val lowerText = document.text().lowercase()
        return when {
            listOf("verify you are human", "checking your browser", "captcha", "enable javascript to continue")
                .any(lowerText::contains) -> "This page uses a browser check that Let’sStudy can’t pass. Paste the job description instead."
            listOf("sign in to continue", "log in to continue", "login required", "access denied")
                .any(lowerText::contains) -> "This job page requires sign-in or blocks automated reading. Paste the job description instead."
            else -> null
        }
    }

    private fun isPublic(host: String): Boolean = try {
        val addresses = client.dns.lookup(host)
        addresses.isNotEmpty() && addresses.all(isAddressPublic)
    } catch (_: Exception) {
        false
    }

    private fun HttpUrl.isPublicHttps(): Boolean =
        scheme == "https" && username.isEmpty() && password.isEmpty() && host.isNotBlank()

    private fun unreadable(url: String, message: String) = JobOfferSource(
        canonicalUrl = url,
        title = "Job listing",
        extractedText = "",
        readable = false,
        reason = message,
    )

    private fun String.cleanText(): String = replace(Regex("\\s+"), " ").trim()

    private class PageTooLargeException : IOException()

    companion object {
        const val MAX_RESPONSE_BYTES = 2_000_000
        private const val MAX_REDIRECTS = 5
        private const val BODY_BUFFER_SIZE = 8 * 1024
        private const val MIN_TEXT_CHARS = 120
        private const val MAX_TITLE_CHARS = 180
        private const val MAX_EXTRACTED_CHARS = 18_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Let\u0027sStudy) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val JOB_TERMS = Regex("\\b(job|jobs|role|position|vacancy|vacancies|career|responsibilit(?:y|ies)|requirement|qualification|experience|skills|apply|employment|empleo|trabajo|vacante|responsabilidades|requisitos|cualificaciones|experiencia)\\b", RegexOption.IGNORE_CASE)
        private val JOB_PATH_TERMS = Regex("job|career|vacan|emple|trabaj", RegexOption.IGNORE_CASE)

        private fun defaultClient() = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()

        private fun isPublicAddress(address: InetAddress): Boolean {
            val bytes = address.address.map { it.toInt() and 0xff }
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false

            if (address is Inet4Address) return isPublicIpv4(bytes)
            if (address is Inet6Address) {
                if (bytes.size != 16) return false
                if (bytes.take(10).all { it == 0 } && bytes[10] == 0xff && bytes[11] == 0xff) {
                    return isPublicIpv4(bytes.takeLast(4))
                }
                // Permit global-unicast 2000::/3 only, and reject the documentation prefix.
                val globalUnicast = bytes[0] in 0x20..0x3f
                val documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
                return globalUnicast && !documentation
            }
            return false
        }

        private fun isPublicIpv4(bytes: List<Int>): Boolean {
            if (bytes.size != 4) return false
            val (a, b, c) = bytes
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && (b == 168 || b == 0 || b == 2 || b == 88 && c == 99)) return false
            if (a == 198 && b in 18..19) return false
            if (a == 198 && b == 51 && c == 100) return false
            if (a == 203 && b == 0 && c == 113) return false
            return true
        }
    }
}
