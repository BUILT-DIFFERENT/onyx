package com.onyx.android.ui

import com.onyx.android.pdf.PdfTextSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteEditorPdfContentTest {
    @Test
    fun `clipboardTextForSelection returns null when selection missing`() {
        assertNull(clipboardTextForSelection(selection = null))
    }

    @Test
    fun `clipboardTextForSelection returns null for blank selection text`() {
        val selection = createSelection("   ")

        assertNull(clipboardTextForSelection(selection))
    }

    @Test
    fun `clipboardTextForSelection returns selected text for non-blank content`() {
        val selection = createSelection("Hello PDF")

        assertEquals("Hello PDF", clipboardTextForSelection(selection))
    }
}

private fun createSelection(text: String): TextSelection =
    TextSelection(
        pageIndex = 0,
        pageCharacters = emptyList(),
        startCharIndex = 0,
        endCharIndex = 0,
        selection = PdfTextSelection(chars = emptyList(), text = text),
    )
