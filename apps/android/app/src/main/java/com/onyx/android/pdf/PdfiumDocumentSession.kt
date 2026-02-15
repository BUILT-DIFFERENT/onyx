package com.onyx.android.pdf

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale

private val PASSWORD_ERROR_MARKERS =
    listOf(
        "password",
        "encrypted",
        "security",
        "decrypt",
    )

data class PdfPageInfo(
    val widthPoints: Float,
    val heightPoints: Float,
)

data class PdfDocumentInfo(
    val pageCount: Int,
    val pages: List<PdfPageInfo>,
)

data class PdfRenderRegion(
    val startX: Int,
    val startY: Int,
    val width: Int,
    val height: Int,
)

/**
 * Represents a bookmark/outline item in a PDF's table of contents.
 * @param title The display text of the bookmark
 * @param pageIndex The zero-based page index this bookmark points to (-1 if no page target)
 * @param children Nested child bookmarks for hierarchical outlines
 */
data class OutlineItem(
    val title: String,
    val pageIndex: Int,
    val children: List<OutlineItem> = emptyList(),
)

class PdfPasswordRequiredException(
    cause: Throwable? = null,
) : IOException("This PDF requires a password.", cause)

class PdfIncorrectPasswordException(
    cause: Throwable? = null,
) : IOException("The PDF password is incorrect.", cause)

interface PdfDocumentInfoReader {
    @Throws(IOException::class)
    fun read(
        pdfFile: File,
        password: String? = null,
    ): PdfDocumentInfo

    @Throws(IOException::class)
    fun readTableOfContents(
        pdfFile: File,
        password: String? = null,
    ): List<OutlineItem>
}

internal fun isLikelyPdfPasswordFailure(message: String?): Boolean {
    if (message.isNullOrBlank()) {
        return false
    }
    val normalized = message.lowercase(Locale.US)
    return PASSWORD_ERROR_MARKERS.any { marker -> normalized.contains(marker) }
}

internal fun classifyPdfiumOpenError(
    error: Throwable,
    password: String?,
): Throwable {
    if (!isLikelyPdfPasswordFailure(error.message)) {
        return error
    }
    return if (password.isNullOrBlank()) {
        PdfPasswordRequiredException(error)
    } else {
        PdfIncorrectPasswordException(error)
    }
}

class PdfiumDocumentInfoReader(
    private val context: Context,
) : PdfDocumentInfoReader {
    @Throws(IOException::class)
    override fun read(
        pdfFile: File,
        password: String?,
    ): PdfDocumentInfo =
        PdfiumDocumentSession.open(
            context = context,
            pdfFile = pdfFile,
            password = password,
        ).use { session ->
            val pageCount = session.pageCount
            val pages =
                (0 until pageCount).map { pageIndex ->
                    val (widthPoints, heightPoints) = session.getPageBounds(pageIndex)
                    PdfPageInfo(widthPoints = widthPoints, heightPoints = heightPoints)
                }
            PdfDocumentInfo(pageCount = pageCount, pages = pages)
        }

    @Throws(IOException::class)
    override fun readTableOfContents(
        pdfFile: File,
        password: String?,
    ): List<OutlineItem> =
        PdfiumDocumentSession.open(
            context = context,
            pdfFile = pdfFile,
            password = password,
        ).use { session ->
            session.getTableOfContents()
        }
}

internal class PdfiumDocumentSession private constructor(
    private val pdfiumCore: PdfiumCore,
    private val pdfDocument: PdfDocument,
) : Closeable {
    private val openedPages = mutableSetOf<Int>()
    private var closed = false

    val pageCount: Int = pdfiumCore.getPageCount(pdfDocument)

    fun getPageBounds(pageIndex: Int): Pair<Float, Float> {
        ensureOpen()
        ensurePageOpened(pageIndex)
        val widthPoints = pdfiumCore.getPageWidthPoint(pdfDocument, pageIndex).toFloat()
        val heightPoints = pdfiumCore.getPageHeightPoint(pdfDocument, pageIndex).toFloat()
        return Pair(widthPoints, heightPoints)
    }

    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap,
        region: PdfRenderRegion,
        renderAnnotations: Boolean = true,
    ) {
        ensureOpen()
        ensurePageOpened(pageIndex)
        pdfiumCore.renderPageBitmap(
            pdfDocument,
            bitmap,
            pageIndex,
            region.startX,
            region.startY,
            region.width,
            region.height,
            renderAnnotations,
        )
    }

    /**
     * Retrieves the table of contents (bookmarks/outline) from the PDF.
     * @return A list of top-level outline items, each potentially containing nested children.
     */
    fun getTableOfContents(): List<OutlineItem> {
        ensureOpen()
        val bookmarks = pdfiumCore.getTableOfContents(pdfDocument)
        return bookmarks.map { it.toOutlineItem() }
    }

    /**
     * Recursively converts a PdfDocument.Bookmark to an OutlineItem.
     */
    private fun PdfDocument.Bookmark.toOutlineItem(): OutlineItem {
        val childItems = this.children?.map { it.toOutlineItem() } ?: emptyList()
        // PdfiumAndroid Bookmark has pageIndex as Long?, need to handle it
        val targetPageIndex =
            try {
                val method = this.javaClass.getDeclaredMethod("getPageIndex")
                method.invoke(this) as? Long ?: -1L
            } catch (_: Exception) {
                -1L
            }
        return OutlineItem(
            title = this.title ?: "",
            pageIndex = targetPageIndex.toInt(),
            children = childItems,
        )
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        pdfiumCore.closeDocument(pdfDocument)
        openedPages.clear()
    }

    private fun ensurePageOpened(pageIndex: Int) {
        require(pageIndex in 0 until pageCount) {
            "Page index $pageIndex is out of bounds for $pageCount pages"
        }
        if (openedPages.add(pageIndex)) {
            pdfiumCore.openPage(pdfDocument, pageIndex)
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Pdfium document session is already closed." }
    }

    companion object {
        @Throws(IOException::class)
        fun open(
            context: Context,
            pdfFile: File,
            password: String?,
        ): PdfiumDocumentSession {
            val pdfiumCore = PdfiumCore(context.applicationContext)
            val parcelFileDescriptor =
                ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
            val document =
                runCatching {
                    pdfiumCore.newDocument(parcelFileDescriptor, password)
                }.getOrElse { error ->
                    runCatching { parcelFileDescriptor.close() }
                    throw classifyPdfiumOpenError(error, password)
                }
            return PdfiumDocumentSession(
                pdfiumCore = pdfiumCore,
                pdfDocument = document,
            )
        }
    }
}
