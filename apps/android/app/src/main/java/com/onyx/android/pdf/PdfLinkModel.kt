package com.onyx.android.pdf

import android.graphics.RectF

sealed interface PdfLinkTarget {
    data class InternalPage(
        val pageIndex: Int,
    ) : PdfLinkTarget

    data class ExternalUrl(
        val url: String,
    ) : PdfLinkTarget
}

data class PdfPageLink(
    val bounds: RectF,
    val target: PdfLinkTarget,
)
