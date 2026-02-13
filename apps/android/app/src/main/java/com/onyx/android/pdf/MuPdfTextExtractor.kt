package com.onyx.android.pdf

import android.graphics.PointF
import android.util.LruCache
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredText.TextChar
import java.io.Closeable
import java.io.File

private const val PDF_TEXT_CACHE_MAX_ENTRIES = 12

internal class MuPdfTextExtractor(
    pdfFile: File,
) : PdfTextExtractor, Closeable {
    private val lock = Any()
    private val document: Document = Document.openDocument(pdfFile.absolutePath)
    private val textCache = LruCache<Int, StructuredText>(PDF_TEXT_CACHE_MAX_ENTRIES)
    private val characterCache = LruCache<Int, List<PdfTextChar>>(PDF_TEXT_CACHE_MAX_ENTRIES)

    override suspend fun getCharacters(pageIndex: Int): List<PdfTextChar> {
        return synchronized(lock) {
            val cachedCharacters = characterCache.get(pageIndex)
            if (cachedCharacters != null) {
                return@synchronized cachedCharacters
            }
            val characters =
                extractTextStructure(pageIndex)
                    .charSequence()
                    .map { char ->
                        PdfTextChar(
                            char = char.c.toChar(),
                            quad =
                                PdfTextQuad(
                                    p1 = PointF(char.quad.ul_x, char.quad.ul_y),
                                    p2 = PointF(char.quad.ur_x, char.quad.ur_y),
                                    p3 = PointF(char.quad.lr_x, char.quad.lr_y),
                                    p4 = PointF(char.quad.ll_x, char.quad.ll_y),
                                ),
                            pageIndex = pageIndex,
                        )
                    }.toList()
            characterCache.put(pageIndex, characters)
            characters
        }
    }

    override fun close() {
        synchronized(lock) {
            textCache.evictAll()
            characterCache.evictAll()
            document.destroy()
        }
    }

    private fun extractTextStructure(pageIndex: Int): StructuredText {
        val cachedText = textCache.get(pageIndex)
        if (cachedText != null) {
            return cachedText
        }
        val page = document.loadPage(pageIndex)
        return try {
            page.toStructuredText().also { text -> textCache.put(pageIndex, text) }
        } finally {
            page.destroy()
        }
    }
}

private fun StructuredText.charSequence(): Sequence<TextChar> =
    blocks
        .asSequence()
        .flatMap { block -> block.lines.asSequence() }
        .flatMap { line -> line.chars.asSequence() }
