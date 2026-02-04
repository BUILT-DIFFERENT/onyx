package com.onyx.android.pdf

import android.graphics.Bitmap
import android.util.Log
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredText.TextChar
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File

class PdfRenderer(
    pdfFile: File,
) {
    private val document: Document = Document.openDocument(pdfFile.absolutePath)

    fun getPageCount(): Int = document.countPages()

    fun getPageBounds(pageIndex: Int): Pair<Float, Float> {
        val page = document.loadPage(pageIndex)
        val bounds = page.bounds
        val width = bounds.x1 - bounds.x0
        val height = bounds.y1 - bounds.y0
        page.destroy()
        Log.d("PdfRenderer", "Page $pageIndex bounds: ${width}pt x ${height}pt")
        return Pair(width, height)
    }

    fun renderPage(
        pageIndex: Int,
        zoom: Float,
    ): Bitmap {
        val page = document.loadPage(pageIndex)
        val bounds = page.bounds
        val pageWidth = bounds.x1 - bounds.x0
        val pageHeight = bounds.y1 - bounds.y0
        Log.d("PdfRenderer", "Page $pageIndex bounds: ${pageWidth}pt x ${pageHeight}pt")

        val matrix = Matrix(zoom)
        val pixelWidth = (pageWidth * zoom).toInt().coerceAtLeast(1)
        val pixelHeight = (pageHeight * zoom).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        val device = AndroidDrawDevice(bitmap)
        try {
            page.run(device, matrix, null)
        } finally {
            device.close()
            page.destroy()
        }
        return bitmap
    }

    fun extractTextStructure(pageIndex: Int): StructuredText {
        val page = document.loadPage(pageIndex)
        val text = page.toStructuredText()
        page.destroy()
        return text
    }

    fun findCharAtPagePoint(
        text: StructuredText,
        pageX: Float,
        pageY: Float,
    ): TextChar? {
        for (block in text.blocks) {
            for (line in block.lines) {
                for (char in line.chars) {
                    if (char.quad.contains(pageX, pageY)) {
                        return char
                    }
                }
            }
        }
        return null
    }

    fun getSelectionQuads(
        text: StructuredText,
        startChar: TextChar,
        endChar: TextChar,
    ): List<Quad> {
        val quads = mutableListOf<Quad>()
        var collecting = false

        for (block in text.blocks) {
            for (line in block.lines) {
                for (char in line.chars) {
                    if (char == startChar) {
                        collecting = true
                    }
                    if (collecting) {
                        quads.add(char.quad)
                    }
                    if (char == endChar) {
                        return quads
                    }
                }
            }
        }
        return quads
    }

    fun extractSelectedText(
        text: StructuredText,
        startChar: TextChar,
        endChar: TextChar,
    ): String {
        val builder = StringBuilder()
        var collecting = false

        for (block in text.blocks) {
            for (line in block.lines) {
                for (char in line.chars) {
                    if (char == startChar) {
                        collecting = true
                    }
                    if (collecting) {
                        builder.append(char.c.toChar())
                    }
                    if (char == endChar) {
                        return builder.toString()
                    }
                }
            }
        }
        return builder.toString()
    }

    fun close() {
        document.destroy()
    }
}
