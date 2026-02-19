package com.onyx.android.pdf

interface PdfTextEngine {
    fun getCharacters(pageIndex: Int): List<PdfTextChar>
}
