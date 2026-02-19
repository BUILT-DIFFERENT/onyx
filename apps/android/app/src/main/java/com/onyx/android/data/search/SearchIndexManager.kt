package com.onyx.android.data.search

import android.content.Context
import android.util.Log
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.PdfTextIndexDao
import com.onyx.android.data.entity.PdfTextIndexEntity
import com.onyx.android.pdf.PdfAssetStorage
import com.onyx.android.pdf.PdfiumRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SearchIndexManager"
private const val CURRENT_EXTRACTOR_VERSION = "1.0"

@Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
class SearchIndexManager(
    private val context: Context,
    private val pageDao: PageDao,
    private val pdfTextIndexDao: PdfTextIndexDao,
    private val pdfAssetStorage: PdfAssetStorage,
) {
    sealed class RebuildStatus {
        data class InProgress(
            val progress: Float,
        ) : RebuildStatus()

        data class Complete(
            val indexedCount: Int,
        ) : RebuildStatus()

        data class Error(
            val message: String,
        ) : RebuildStatus()
    }

    suspend fun rebuildPdfIndex(onProgress: (RebuildStatus) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            var indexedCount = 0

            try {
                val pdfDir = File(context.filesDir, "pdf_assets")
                if (!pdfDir.exists()) {
                    onProgress(RebuildStatus.Complete(0))
                    return@withContext 0
                }

                val pdfFiles = pdfDir.listFiles()?.filter { it.extension == "pdf" } ?: emptyList()
                val totalFiles = pdfFiles.size

                if (totalFiles == 0) {
                    onProgress(RebuildStatus.Complete(0))
                    return@withContext 0
                }

                pdfFiles.forEachIndexed { index, pdfFile ->
                    val assetId = pdfFile.nameWithoutExtension
                    onProgress(RebuildStatus.InProgress(index.toFloat() / totalFiles))

                    try {
                        indexedCount += indexPdfAsset(assetId, pdfFile)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to index PDF $assetId", e)
                    }
                }

                onProgress(RebuildStatus.Complete(indexedCount))
                indexedCount
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rebuild PDF index", e)
                onProgress(RebuildStatus.Error(e.message ?: "Unknown error"))
                0
            }
        }

    private suspend fun indexPdfAsset(
        assetId: String,
        pdfFile: File,
    ): Int {
        var indexedCount = 0
        val renderer = PdfiumRenderer(context, pdfFile)

        try {
            val pageCount = renderer.getPageCount()
            val now = System.currentTimeMillis()

            for (pageIndex in 0 until pageCount) {
                val textChars = renderer.getCharacters(pageIndex)
                val extractedText = textChars.joinToString("") { charData -> charData.char }

                if (extractedText.isNotBlank()) {
                    val pages =
                        pageDao.getPagesForNoteSync("").filter {
                            it.pdfAssetId == assetId && it.pdfPageNo == pageIndex + 1
                        }

                    for (page in pages) {
                        pdfTextIndexDao.insert(
                            PdfTextIndexEntity(
                                pageId = page.pageId,
                                pdfAssetId = assetId,
                                pageNo = pageIndex,
                                extractedText = extractedText,
                                extractedAt = now,
                                extractorVersion = CURRENT_EXTRACTOR_VERSION,
                            ),
                        )
                        indexedCount++
                    }
                }
            }
        } finally {
            renderer.close()
        }

        return indexedCount
    }

    suspend fun indexPdfPage(
        pageId: String,
        pdfAssetId: String,
        pageNo: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val pdfFile = pdfAssetStorage.getFileForAsset(pdfAssetId)
                if (!pdfFile.exists()) {
                    Log.w(TAG, "PDF file not found: $pdfAssetId")
                    return@withContext false
                }

                val renderer = PdfiumRenderer(context, pdfFile)
                try {
                    val textChars = renderer.getCharacters(pageNo)
                    val extractedText = textChars.joinToString("") { charData -> charData.char }
                    val now = System.currentTimeMillis()

                    pdfTextIndexDao.insert(
                        PdfTextIndexEntity(
                            pageId = pageId,
                            pdfAssetId = pdfAssetId,
                            pageNo = pageNo,
                            extractedText = extractedText.ifBlank { null },
                            extractedAt = now,
                            extractorVersion = CURRENT_EXTRACTOR_VERSION,
                        ),
                    )
                    true
                } finally {
                    renderer.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index PDF page $pageId", e)
                false
            }
        }

    suspend fun isIndexValid(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val count = pdfTextIndexDao.getCount()
                count > 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check index validity", e)
                false
            }
        }

    suspend fun clearIndex() =
        withContext(Dispatchers.IO) {
            try {
                val pdfDir = File(context.filesDir, "pdf_assets")
                if (pdfDir.exists()) {
                    pdfDir
                        .listFiles()
                        ?.filter { it.extension == "pdf" }
                        ?.forEach { pdfFile ->
                            val assetId = pdfFile.nameWithoutExtension
                            pdfTextIndexDao.deleteByPdfAssetId(assetId)
                        }
                }
                Log.i(TAG, "PDF text index cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear index", e)
            }
        }
}
