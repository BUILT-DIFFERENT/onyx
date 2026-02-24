package com.onyx.android.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageBreakdown(
    val notesBytes: Long,
    val imagesBytes: Long,
    val pdfBytes: Long,
    val audioBytes: Long,
    val cacheBytes: Long,
    val thumbnailsBytes: Long,
    val exportsBytes: Long,
) {
    val totalBytes: Long
        get() =
            notesBytes +
                imagesBytes +
                pdfBytes +
                audioBytes +
                cacheBytes +
                thumbnailsBytes +
                exportsBytes
}

class StorageRepository(
    private val context: Context,
) {
    suspend fun getStorageBreakdown(): StorageBreakdown =
        withContext(Dispatchers.IO) {
            val filesDir = context.filesDir
            val noteDbDir = context.getDatabasePath("onyx.db").parentFile
            val thumbnailsDir = File(filesDir, "thumbnails")
            val pdfAssetsDir = File(filesDir, "pdf_assets")
            val exportsDir = File(filesDir, "exports")
            val imagesDir = File(filesDir, "images")
            val audioDir = File(filesDir, "audio")

            StorageBreakdown(
                notesBytes = sizeOfDirectory(noteDbDir),
                imagesBytes = sizeOfDirectory(imagesDir),
                pdfBytes = sizeOfDirectory(pdfAssetsDir),
                audioBytes = sizeOfDirectory(audioDir),
                cacheBytes = sizeOfDirectory(context.cacheDir),
                thumbnailsBytes = sizeOfDirectory(thumbnailsDir),
                exportsBytes = sizeOfDirectory(exportsDir),
            )
        }

    suspend fun clearCacheData() =
        withContext(Dispatchers.IO) {
            deleteChildren(context.cacheDir)
            deleteChildren(File(context.filesDir, "thumbnails"))
            deleteChildren(File(context.filesDir, "exports"))
        }

    private fun sizeOfDirectory(file: File?): Long {
        val existingFile = file?.takeIf { it.exists() } ?: return 0L
        val nestedSize = existingFile.listFiles()?.sumOf(::sizeOfDirectory) ?: 0L
        return if (existingFile.isFile) existingFile.length() else nestedSize
    }

    private fun deleteChildren(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return
        }
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteRecursively(child)
            } else {
                child.delete()
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }
}
