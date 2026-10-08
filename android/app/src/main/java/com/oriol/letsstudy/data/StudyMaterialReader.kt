package com.oriol.letsstudy.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val text = when {
            name.endsWith(".pdf", true) -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                try {
                    PDDocument.load(bytes).use { document ->
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
        require(text.length >= 100) { "No usable text was found. Scanned PDFs need OCR; paste the text instead." }
        ImportedMaterial(name, text.take(18_000))
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
