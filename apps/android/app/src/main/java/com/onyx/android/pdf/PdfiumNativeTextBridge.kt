package com.onyx.android.pdf

internal const val PDFIUM_CHAR_BOX_VALUES_PER_CHAR = 4

private const val PDFIUM_LEFT_OFFSET = 0
private const val PDFIUM_RIGHT_OFFSET = 1
private const val PDFIUM_BOTTOM_OFFSET = 2
private const val PDFIUM_TOP_OFFSET = 3

internal data class PdfiumNativeTextPage(
    val codePoints: IntArray,
    val boxes: FloatArray,
) {
    init {
        require(boxes.size == codePoints.size * PDFIUM_CHAR_BOX_VALUES_PER_CHAR) {
            "boxes must contain exactly 4 float values per code point."
        }
    }

    val isEmpty: Boolean
        get() = codePoints.isEmpty()
}

internal object PdfiumNativeTextBridge {
    private const val LIBRARY_NAME = "onyx_pdfium_text"

    @Volatile
    private var nativeLibraryLoadAttempted = false

    @Volatile
    private var nativeLibraryAvailable = false

    fun extractPageText(pagePointer: Long): PdfiumNativeTextPage? {
        if (pagePointer == 0L || !ensureNativeLibraryLoaded()) {
            return null
        }
        return runCatching {
            nativeExtractPageText(pagePointer)
        }.getOrNull()
    }

    fun getPageRotation(pagePointer: Long): Int {
        if (pagePointer == 0L || !ensureNativeLibraryLoaded()) {
            return 0
        }
        return runCatching {
            nativeGetPageRotation(pagePointer)
        }.getOrDefault(0)
    }

    private fun ensureNativeLibraryLoaded(): Boolean {
        if (nativeLibraryLoadAttempted) {
            return nativeLibraryAvailable
        }
        synchronized(this) {
            if (!nativeLibraryLoadAttempted) {
                nativeLibraryAvailable =
                    runCatching {
                        System.loadLibrary(LIBRARY_NAME)
                    }.isSuccess
                nativeLibraryLoadAttempted = true
            }
        }
        return nativeLibraryAvailable
    }

    @JvmStatic
    private external fun nativeExtractPageText(pagePointer: Long): PdfiumNativeTextPage?

    @JvmStatic
    private external fun nativeGetPageRotation(pagePointer: Long): Int
}

internal fun PdfiumNativeTextPage.toPdfTextChars(
    pageIndex: Int,
    pageHeightPoints: Float,
): List<PdfTextChar> {
    if (isEmpty) {
        return emptyList()
    }
    return buildList(codePoints.size) {
        codePoints.indices.forEach { charIndex ->
            val codePoint = codePoints[charIndex]
            if (!Character.isValidCodePoint(codePoint) || codePoint == 0) {
                return@forEach
            }

            val boxOffset = charIndex * PDFIUM_CHAR_BOX_VALUES_PER_CHAR
            val left = boxes[boxOffset + PDFIUM_LEFT_OFFSET]
            val right = boxes[boxOffset + PDFIUM_RIGHT_OFFSET]
            val bottom = boxes[boxOffset + PDFIUM_BOTTOM_OFFSET]
            val top = boxes[boxOffset + PDFIUM_TOP_OFFSET]

            add(
                PdfTextChar(
                    char = String(Character.toChars(codePoint)),
                    pageIndex = pageIndex,
                    quad = charBoxToQuad(left, right, bottom, top, pageHeightPoints),
                ),
            )
        }
    }
}

internal fun charBoxToQuad(
    left: Float,
    right: Float,
    bottom: Float,
    top: Float,
    pageHeightPoints: Float,
): PdfTextQuad {
    val normalizedLeft = minOf(left, right)
    val normalizedRight = maxOf(left, right)
    val topY = (pageHeightPoints - top)
    val bottomY = (pageHeightPoints - bottom)

    val normalizedTop = minOf(topY, bottomY)
    val normalizedBottom = maxOf(topY, bottomY)

    return PdfTextQuad(
        p1 = pointF(normalizedLeft, normalizedTop),
        p2 = pointF(normalizedRight, normalizedTop),
        p3 = pointF(normalizedRight, normalizedBottom),
        p4 = pointF(normalizedLeft, normalizedBottom),
    )
}

internal fun pointF(
    x: Float,
    y: Float,
): android.graphics.PointF =
    android.graphics.PointF().apply {
        this.x = x
        this.y = y
    }
