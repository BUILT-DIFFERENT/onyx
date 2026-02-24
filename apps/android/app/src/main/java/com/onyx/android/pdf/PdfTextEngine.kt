package com.onyx.android.pdf

interface PdfTextEngine {
    fun getCharacters(pageIndex: Int): List<PdfTextChar>

    fun getLinks(pageIndex: Int): List<PdfPageLink> = emptyList()
}
