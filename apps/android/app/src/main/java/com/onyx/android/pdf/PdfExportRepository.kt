package com.onyx.android.pdf

import android.content.Context
import com.onyx.android.data.entity.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PdfExportArtifactMetadata(
    val exportId: String,
    val noteId: String,
    val ownerUserId: String,
    val format: String = "pdf",
    val mode: String,
    val status: String = "succeeded",
    val assetId: String,
    val requestedAt: Long,
    val completedAt: Long,
    val errorMessage: String? = null,
)

data class PdfExportArtifact(
    val exportId: String,
    val pdfFile: File,
    val metadataFile: File,
    val flatten: Boolean,
)

class PdfExportRepository(
    private val context: Context,
    private val pdfAssetStorage: PdfAssetStorage,
) {
    companion object {
        private const val MAX_EXPORT_NAME_LENGTH = 64
    }

    private val json = Json { prettyPrint = true }

    suspend fun exportNotePdf(
        note: NoteEntity,
        pdfAssetId: String,
        flatten: Boolean,
    ): PdfExportArtifact =
        withContext(Dispatchers.IO) {
            val exportsDir = File(context.filesDir, "exports").also { it.mkdirs() }
            val exportId = UUID.randomUUID().toString()
            val requestedAt = System.currentTimeMillis()
            val sourceFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
            check(sourceFile.exists()) { "PDF asset not found for export." }

            val mode = if (flatten) "flattened" else "layered"
            val safeTitle = sanitizeFileName(note.title.ifBlank { "note" })
            val outputName = "${safeTitle}_$mode.pdf"
            val outputFile = File(exportsDir, outputName)
            sourceFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val metadata =
                PdfExportArtifactMetadata(
                    exportId = exportId,
                    noteId = note.noteId,
                    ownerUserId = note.ownerUserId,
                    mode = mode,
                    assetId = outputName,
                    requestedAt = requestedAt,
                    completedAt = System.currentTimeMillis(),
                )
            val metadataFile = File(exportsDir, "${outputName.removeSuffix(".pdf")}.metadata.json")
            metadataFile.writeText(json.encodeToString(metadata))

            PdfExportArtifact(
                exportId = exportId,
                pdfFile = outputFile,
                metadataFile = metadataFile,
                flatten = flatten,
            )
        }

    private fun sanitizeFileName(input: String): String =
        input
            .trim()
            .ifBlank { "note" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(MAX_EXPORT_NAME_LENGTH)
}
