package com.onyx.android.ui

import com.onyx.android.pdf.PdfIncorrectPasswordException
import com.onyx.android.pdf.PdfPasswordRequiredException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteEditorScreenPdfOpenFailureTest {
    @Test
    fun `password required exception maps to password prompt`() {
        val action = mapPdfOpenFailureToUiAction(PdfPasswordRequiredException())

        assertEquals(
            PdfOpenFailureUiAction.PromptForPassword(isIncorrectPassword = false),
            action,
        )
    }

    @Test
    fun `incorrect password exception maps to incorrect password prompt`() {
        val action = mapPdfOpenFailureToUiAction(PdfIncorrectPasswordException())

        assertEquals(
            PdfOpenFailureUiAction.PromptForPassword(isIncorrectPassword = true),
            action,
        )
    }

    @Test
    fun `blank message maps to default open error`() {
        val action = mapPdfOpenFailureToUiAction(IllegalStateException(""))

        assertEquals(
            PdfOpenFailureUiAction.ShowOpenError(message = "Unable to open this PDF."),
            action,
        )
    }

    @Test
    fun `non blank message maps to explicit open error`() {
        val action = mapPdfOpenFailureToUiAction(IllegalStateException("missing file"))

        assertEquals(
            PdfOpenFailureUiAction.ShowOpenError(message = "missing file"),
            action,
        )
    }
}
