package com.onyx.android.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfiumRendererTextExtractionTest {
    @Test
    fun getCharacters_extractsGlyphsFromGeneratedPdf() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val pdfFile = File.createTempFile("pdfium-text-", ".pdf", context.cacheDir)
            createPdfWithText(pdfFile, "Hello Pdfium")

            val renderer = PdfiumRenderer(context, pdfFile)
            try {
                val chars = renderer.getCharacters(pageIndex = 0)
                assertFalse("Expected extracted glyphs from generated PDF", chars.isEmpty())
                val extracted = chars.joinToString(separator = "") { it.char }
                assertTrue(
                    "Expected extracted text to contain greeting, got: $extracted",
                    extracted.contains("Hello"),
                )
            } finally {
                renderer.close()
                pdfFile.delete()
            }
        }

    private fun createPdfWithText(
        output: File,
        text: String,
    ) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 800, 1).create()
        val page = pdf.startPage(pageInfo)
        val paint =
            Paint().apply {
                textSize = 42f
                isAntiAlias = true
            }
        page.canvas.drawText(text, 40f, 120f, paint)
        pdf.finishPage(page)
        FileOutputStream(output).use { stream ->
            pdf.writeTo(stream)
        }
        pdf.close()
    }
}
