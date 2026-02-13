package com.onyx.android.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfiumIntegrationSmokeTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun importedPdf_canBeReadAndRenderedWithPdfium() {
        val sourcePdf = File.createTempFile("pdfium-smoke-source", ".pdf", context.cacheDir)
        val storage = PdfAssetStorage(context)
        var importedAssetId: String? = null

        createSinglePagePdf(sourcePdf)

        try {
            importedAssetId = storage.importPdf(sourcePdf.toUri())
            assertTrue(storage.assetExists(importedAssetId))

            val importedFile = storage.getFileForAsset(importedAssetId)
            val infoReader = PdfiumDocumentInfoReader(context)
            val documentInfo = infoReader.read(importedFile)
            assertEquals(1, documentInfo.pageCount)
            assertEquals(1, documentInfo.pages.size)
            assertTrue(documentInfo.pages[0].widthPoints > 0f)
            assertTrue(documentInfo.pages[0].heightPoints > 0f)

            val renderer = PdfiumRenderer(context, importedFile)
            try {
                val bitmap1x = renderer.renderPage(pageIndex = 0, zoom = 1f)
                val bitmap2x = renderer.renderPage(pageIndex = 0, zoom = 2f)
                assertTrue(bitmap1x.width > 0)
                assertTrue(bitmap1x.height > 0)
                assertTrue(bitmap2x.width > bitmap1x.width)
                assertTrue(bitmap2x.height > bitmap1x.height)
            } finally {
                renderer.close()
            }
        } finally {
            importedAssetId?.let(storage::deleteAsset)
            sourcePdf.delete()
        }
    }

    private fun createSinglePagePdf(outputFile: File) {
        val document = PdfDocument()
        try {
            val pageInfo =
                PdfDocument.PageInfo
                    .Builder(300, 300, 1)
                    .create()
            val page = document.startPage(pageInfo)
            page.canvas.drawText("Pdfium smoke test", 24f, 160f, Paint().apply { textSize = 16f })
            document.finishPage(page)
            outputFile.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
