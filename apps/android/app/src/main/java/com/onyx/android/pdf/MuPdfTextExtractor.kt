package com.onyx.android.pdf

import android.graphics.PointF
import android.util.LruCache
import java.io.Closeable
import java.io.File

private const val DOCUMENT_CLASS_NAME = "com.artifex.mupdf.fitz.Document"
private const val PDF_TEXT_CACHE_MAX_ENTRIES = 12
private const val REPLACEMENT_CHARACTER = "\uFFFD"

internal class MuPdfTextExtractor(
    pdfFile: File,
) : PdfTextExtractor, Closeable {
    private val lock = Any()
    private val bridge = MuPdfReflectionBridge()
    private val document = bridge.openDocument(pdfFile)
    private val textCache =
        object : LruCache<Int, Any>(PDF_TEXT_CACHE_MAX_ENTRIES) {
            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Any?,
                newValue: Any?,
            ) {
                if (oldValue !== null && oldValue !== newValue) {
                    bridge.destroy(oldValue)
                }
            }
        }
    private val characterCache = LruCache<Int, List<PdfTextChar>>(PDF_TEXT_CACHE_MAX_ENTRIES)

    override suspend fun getCharacters(pageIndex: Int): List<PdfTextChar> {
        return synchronized(lock) {
            val cachedCharacters = characterCache.get(pageIndex)
            if (cachedCharacters != null) {
                return@synchronized cachedCharacters
            }
            val structuredText = extractTextStructure(pageIndex)
            val extractedCharacters = bridge.extractCharacters(structuredText, pageIndex)
            characterCache.put(pageIndex, extractedCharacters)
            extractedCharacters
        }
    }

    override fun close() {
        synchronized(lock) {
            textCache.evictAll()
            characterCache.evictAll()
            bridge.destroy(document)
        }
    }

    private fun extractTextStructure(pageIndex: Int): Any {
        val cachedText = textCache.get(pageIndex)
        if (cachedText != null) {
            return cachedText
        }
        val page = bridge.loadPage(document, pageIndex)
        return try {
            bridge.toStructuredText(page).also { text -> textCache.put(pageIndex, text) }
        } finally {
            bridge.destroy(page)
        }
    }
}

private class MuPdfReflectionBridge {
    private val documentClass = Class.forName(DOCUMENT_CLASS_NAME)
    private val openDocumentMethod = documentClass.getMethod("openDocument", String::class.java)
    private val loadPageMethod = documentClass.getMethod("loadPage", Int::class.javaPrimitiveType)
    private val destroyMethods = mutableMapOf<Class<*>, java.lang.reflect.Method?>()

    fun openDocument(pdfFile: File): Any = requireNotNull(openDocumentMethod.invoke(null, pdfFile.absolutePath))

    fun loadPage(
        document: Any,
        pageIndex: Int,
    ): Any = requireNotNull(loadPageMethod.invoke(document, pageIndex))

    fun toStructuredText(page: Any): Any = requireNotNull(page.javaClass.getMethod("toStructuredText").invoke(page))

    fun extractCharacters(
        structuredText: Any,
        pageIndex: Int,
    ): List<PdfTextChar> {
        val result = ArrayList<PdfTextChar>()
        val blocks = readArray(structuredText, "blocks")
        for (block in blocks) {
            val lines = readArray(block, "lines")
            for (line in lines) {
                val chars = readArray(line, "chars")
                for (char in chars) {
                    result += toPdfTextChar(char, pageIndex)
                }
            }
        }
        return result
    }

    fun destroy(instance: Any) {
        val method =
            destroyMethods.getOrPut(instance.javaClass) {
                runCatching { instance.javaClass.getMethod("destroy") }.getOrNull()
            } ?: return
        method.invoke(instance)
    }

    private fun toPdfTextChar(
        textChar: Any,
        pageIndex: Int,
    ): PdfTextChar {
        val codePoint = textChar.javaClass.getField("c").getInt(textChar)
        val quad = requireNotNull(textChar.javaClass.getField("quad").get(textChar))
        return PdfTextChar(
            char = codePoint.toUnicodeString(),
            quad =
                PdfTextQuad(
                    p1 = PointF(readFloat(quad, "ul_x"), readFloat(quad, "ul_y")),
                    p2 = PointF(readFloat(quad, "ur_x"), readFloat(quad, "ur_y")),
                    p3 = PointF(readFloat(quad, "lr_x"), readFloat(quad, "lr_y")),
                    p4 = PointF(readFloat(quad, "ll_x"), readFloat(quad, "ll_y")),
                ),
            pageIndex = pageIndex,
        )
    }

    private fun readArray(
        owner: Any,
        fieldName: String,
    ): List<Any> {
        val value = owner.javaClass.getField(fieldName).get(owner)
        val result = ArrayList<Any>()
        if (value != null && value.javaClass.isArray) {
            val size = java.lang.reflect.Array.getLength(value)
            result.ensureCapacity(size)
            for (index in 0 until size) {
                val element = java.lang.reflect.Array.get(value, index)
                if (element != null) {
                    result += element
                }
            }
        }
        return result
    }

    private fun readFloat(
        owner: Any,
        fieldName: String,
    ): Float = owner.javaClass.getField(fieldName).getFloat(owner)
}

private fun Int.toUnicodeString(): String =
    if (Character.isValidCodePoint(this)) {
        String(Character.toChars(this))
    } else {
        REPLACEMENT_CHARACTER
    }
