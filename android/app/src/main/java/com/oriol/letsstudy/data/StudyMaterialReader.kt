package com.oriol.letsstudy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import java.io.InputStream
import java.io.ByteArrayOutputStream

data class ImportedMaterial(val name: String, val text: String)
private class TextLimitReached : RuntimeException()

class StudyMaterialReader(private val context: Context) {
    suspend fun read(uri: Uri): ImportedMaterial = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Study material"
        } ?: "Study material"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(10 * 1024 * 1024 + 1) }
            ?: error("Couldn't open this document. Choose it again.")
        require(bytes.size <= 10 * 1024 * 1024) { "Choose a document smaller than 10 MB." }
        val isPdf = name.endsWith(".pdf", true)
        val text = when {
            isPdf -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                try {
                    val embeddedText = PDDocument.load(bytes).use { document ->
                        require(document.numberOfPages <= 100) { "Choose a PDF with 100 pages or fewer." }
                        val result = StringBuilder()
                        val writer = object : java.io.Writer() {
                            override fun write(chars: CharArray, offset: Int, length: Int) {
                                if (result.length < 18000) result.append(chars, offset, minOf(length, 18000 - result.length))
                                if (result.length >= 18000) throw TextLimitReached()
                            }
                            override fun flush() = Unit
                            override fun close() = Unit
                        }
                        try {
                            val stripper = PDFTextStripper()
                            for (page in 1..document.numberOfPages) {
                                stripper.startPage = page
                                stripper.endPage = page
                                stripper.writeText(document, writer)
                            }
                        } catch (_: TextLimitReached) { }
                        result.toString()
                    }
                    if (embeddedText.length >= MIN_READABLE_TEXT) embeddedText else readScannedPdf(bytes)
                } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                    throw IllegalArgumentException("This PDF is password protected. Choose an unlocked PDF or paste its text.")
                } catch (_: java.io.IOException) {
                    throw IllegalArgumentException("This PDF couldn't be read. Try an undamaged PDF or paste its text.")
                }
            }
            name.endsWith(".pptx", true) -> PresentationTextReader.read(bytes)
            name.endsWith(".txt", true) || context.contentResolver.getType(uri)?.startsWith("text/") == true -> bytes.toString(Charsets.UTF_8)
            else -> error("Choose a PDF, PPTX or text document.")
        }.trim()
        require(text.length >= MIN_READABLE_TEXT) {
            if (isPdf) "No readable text was found. Scanned PDF OCR supports Latin-script text on up to 30 pages; Thai-script scans are not supported yet."
            else "No usable text was found. Check the file or paste its text instead."
        }
        ImportedMaterial(name, text.take(18_000))
    }

    private suspend fun readScannedPdf(bytes: ByteArray): String {
        val temporaryFile = File.createTempFile("letsstudy-scan-", ".pdf", context.cacheDir)
        temporaryFile.writeBytes(bytes)
        try {
            ParcelFileDescriptor.open(temporaryFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val result = StringBuilder()
                        val pageLimit = minOf(renderer.pageCount, MAX_OCR_PAGES)
                        for (pageIndex in 0 until pageLimit) {
                            val page = renderer.openPage(pageIndex)
                            var bitmap: Bitmap? = null
                            try {
                                val scale = MAX_OCR_DIMENSION.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val matrix = Matrix().apply { setScale(scale, scale) }
                                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                                if (recognized.isNotBlank()) result.append("Page ").append(pageIndex + 1).append('\n').append(recognized).append("\n\n")
                            } finally {
                                bitmap?.recycle()
                                page.close()
                            }
                            if (result.length >= MAX_TEXT_CHARS) break
                        }
                        return result.toString().take(MAX_TEXT_CHARS)
                    } finally {
                        recognizer.close()
                    }
                }
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private companion object {
        const val MIN_READABLE_TEXT = 100
        const val MAX_OCR_PAGES = 30
        const val MAX_OCR_DIMENSION = 1600
        const val MAX_TEXT_CHARS = 18_000
    }

}

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

object PresentationTextReader {
    fun read(bytes: ByteArray): String {
        val slides = sortedMapOf<Int, String>()
        var expanded = 0L
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 2500) { "This presentation is too large." }
                val index = Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry.name)?.groupValues?.get(1)?.toIntOrNull()
                val xml = zip.readBounded(if (index != null) 1024 * 1024 + 1 else 20 * 1024 * 1024 + 1)
                expanded += xml.size
                require(expanded <= 20 * 1024 * 1024) { "This presentation expands to too much data." }
                if (index != null) {
                    require(index <= 100) { "Choose a presentation with 100 slides or fewer." }
                    require(xml.size <= 1024 * 1024) { "This presentation contains too much text." }
                    require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml.toString(Charsets.UTF_8).replace("\u0000", ""))) { "Document type and entity declarations are not supported." }
                    val builder = factory.newDocumentBuilder()
                    builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are not supported.") }
                    val document = builder.parse(xml.inputStream())
                    val nodes = document.getElementsByTagNameNS("*", "t")
                    require(index !in slides) { "This presentation contains duplicate slides." }
                    slides[index] = (0 until nodes.length).joinToString("\n") { nodes.item(it).textContent }.take(18000)
                }
                zip.closeEntry()
            }
        }
        return slides.entries.joinToString("\n\n") { "Slide ${it.key}\n${it.value}" }
    }
}
