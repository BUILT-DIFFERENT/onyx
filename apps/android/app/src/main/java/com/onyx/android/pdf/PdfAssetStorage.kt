package com.onyx.android.pdf

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Manages PDF file storage in internal app directory.
 * PDF files are stored as: {filesDir}/pdf_assets/{assetId}.pdf
 *
 * The assetId is a UUID that is referenced by PageEntity.pdfAssetId
 * Multiple pages can share the same pdfAssetId (multi-page PDF).
 */
class PdfAssetStorage(
    private val context: Context,
) {
    private val pdfDir: File
        get() = File(context.filesDir, "pdf_assets").also { it.mkdirs() }

    /**
     * Import a PDF from a content URI (e.g., from file picker).
     * Copies the PDF to internal storage.
     *
     * @param uri Content URI from Storage Access Framework
     * @return Asset ID (UUID string) for the imported PDF
     */
    fun importPdf(uri: Uri): String {
        val assetId = UUID.randomUUID().toString()
        val targetFile = File(pdfDir, "$assetId.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open PDF from URI: $uri")

        return assetId
    }

    /**
     * Get the File for a given asset ID.
     * Use this with the active PDF renderer implementation.
     */
    fun getFileForAsset(assetId: String): File = File(pdfDir, "$assetId.pdf")

    /**
     * Delete a PDF asset (e.g., when note is deleted).
     */
    fun deleteAsset(assetId: String) {
        File(pdfDir, "$assetId.pdf").delete()
    }

    /**
     * Check if asset exists.
     */
    fun assetExists(assetId: String): Boolean = File(pdfDir, "$assetId.pdf").exists()
}
