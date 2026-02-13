package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class PdfiumDocumentSessionTest {
    @Test
    fun `password failure detection matches known markers`() {
        assertTrue(isLikelyPdfPasswordFailure("Password required or incorrect password"))
        assertTrue(isLikelyPdfPasswordFailure("Encrypted document cannot be opened"))
        assertFalse(isLikelyPdfPasswordFailure("File is damaged"))
    }

    @Test
    fun `classify open error marks missing password`() {
        val sourceError = IOException("Password required")
        val classified = classifyPdfiumOpenError(sourceError, password = null)

        assertTrue(classified is PdfPasswordRequiredException)
    }

    @Test
    fun `classify open error marks incorrect password`() {
        val sourceError = IOException("incorrect password")
        val classified = classifyPdfiumOpenError(sourceError, password = "bad-password")

        assertTrue(classified is PdfIncorrectPasswordException)
    }

    @Test
    fun `classify open error returns original error when not password related`() {
        val sourceError = IOException("Not a PDF file")
        val classified = classifyPdfiumOpenError(sourceError, password = null)

        assertSame(sourceError, classified)
    }
}
