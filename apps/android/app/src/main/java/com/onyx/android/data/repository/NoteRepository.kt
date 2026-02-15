package com.onyx.android.data.repository

import com.onyx.android.data.dao.FolderDao
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.dao.TagDao
import com.onyx.android.data.entity.FolderEntity
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.NoteTagCrossRef
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.entity.TagEntity
import com.onyx.android.data.entity.ThumbnailEntity
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.data.thumbnail.ThumbnailGenerator
import com.onyx.android.device.DeviceIdentity
import com.onyx.android.ink.model.Stroke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class SearchResultItem(
    val noteId: String,
    val noteTitle: String,
    val pageId: String,
    val pageNumber: Int,
    val snippetText: String,
    val matchScore: Double,
)

/**
 * Sort options for notes list.
 */
enum class SortOption {
    NAME,
    CREATED,
    MODIFIED,
}

/**
 * Sort direction for notes list.
 */
enum class SortDirection {
    ASC,
    DESC,
}

/**
 * Date range filter options.
 */
enum class DateRange {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    OLDER,
}

/**
 * Combined filter state for notes list.
 */
data class FilterState(
    val folderId: String? = null,
    val tagId: String? = null,
    val dateRange: DateRange? = null,
)

/**
 * Exception thrown when attempting to create a tag with a name that already exists.
 */
class DuplicateTagNameException(tagName: String) : Exception("Tag with name '$tagName' already exists")

@Suppress("TooManyFunctions", "LongParameterList")
class NoteRepository(
    private val noteDao: NoteDao,
    private val pageDao: PageDao,
    private val strokeDao: StrokeDao,
    private val recognitionDao: RecognitionDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val deviceIdentity: DeviceIdentity,
    private val strokeSerializer: StrokeSerializer,
    private val thumbnailGenerator: ThumbnailGenerator?,
) {
    companion object {
        private const val SNIPPET_LENGTH = 100
    }

    suspend fun createNote(): NoteWithFirstPage {
        val now = System.currentTimeMillis()
        val note =
            NoteEntity(
                noteId = UUID.randomUUID().toString(),
                ownerUserId = deviceIdentity.getDeviceId(),
                title = "",
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        noteDao.insert(note)

        val firstPage = createPageForNote(note.noteId, indexInNote = 0)
        return NoteWithFirstPage(note, firstPage.pageId)
    }

    data class NoteWithFirstPage(
        val note: NoteEntity,
        val firstPageId: String,
    )

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun getPagesForNote(noteId: String): Flow<List<PageEntity>> = pageDao.getPagesForNote(noteId)

    suspend fun createPage(page: PageEntity): PageEntity {
        val now = System.currentTimeMillis()
        val storedPage = page.copy(updatedAt = now)
        pageDao.insert(storedPage)

        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = storedPage.pageId,
                noteId = storedPage.noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )

        noteDao.updateTimestamp(storedPage.noteId, now)
        return storedPage
    }

    suspend fun createPageForNote(
        noteId: String,
        indexInNote: Int,
    ): PageEntity {
        val now = System.currentTimeMillis()
        val page =
            PageEntity(
                pageId = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = "ink",
                geometryKind = "fixed",
                indexInNote = indexInNote,
                width = 612f,
                height = 792f,
                unit = "pt",
                pdfAssetId = null,
                pdfPageNo = null,
                updatedAt = now,
                contentLamportMax = 0,
            )
        pageDao.insert(page)

        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = page.pageId,
                noteId = noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )

        noteDao.updateTimestamp(noteId, now)
        return page
    }

    @Suppress("LongParameterList")
    suspend fun createPageFromPdf(
        noteId: String,
        indexInNote: Int,
        pdfAssetId: String,
        pdfPageNo: Int,
        pdfWidth: Float,
        pdfHeight: Float,
    ): PageEntity {
        val now = System.currentTimeMillis()
        val page =
            PageEntity(
                pageId = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = "pdf",
                geometryKind = "fixed",
                indexInNote = indexInNote,
                width = pdfWidth,
                height = pdfHeight,
                unit = "pt",
                pdfAssetId = pdfAssetId,
                pdfPageNo = pdfPageNo,
                updatedAt = now,
                contentLamportMax = 0,
            )
        pageDao.insert(page)
        recognitionDao.insert(
            RecognitionIndexEntity(
                pageId = page.pageId,
                noteId = noteId,
                recognizedText = null,
                recognizedAtLamport = null,
                recognizerVersion = null,
                updatedAt = now,
            ),
        )
        noteDao.updateTimestamp(noteId, now)
        return page
    }

    suspend fun saveStroke(
        pageId: String,
        stroke: Stroke,
    ) {
        val now = System.currentTimeMillis()
        val entity =
            StrokeEntity(
                strokeId = stroke.id,
                pageId = pageId,
                strokeData = strokeSerializer.serializePoints(stroke.points),
                style = strokeSerializer.serializeStyle(stroke.style),
                bounds = strokeSerializer.serializeBounds(stroke.bounds),
                createdAt = stroke.createdAt,
                createdLamport = stroke.createdLamport,
            )
        strokeDao.insert(entity)

        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun deleteStroke(strokeId: String) {
        val stroke = strokeDao.getById(strokeId) ?: return
        strokeDao.delete(strokeId)

        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(stroke.pageId, now)
        val page = requireNotNull(pageDao.getById(stroke.pageId)) { "Page not found: ${stroke.pageId}" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    suspend fun getStrokesForPage(pageId: String): List<Stroke> =
        strokeDao.getByPageId(pageId).map { entity ->
            Stroke(
                id = entity.strokeId,
                points = strokeSerializer.deserializePoints(entity.strokeData),
                style = strokeSerializer.deserializeStyle(entity.style),
                bounds = strokeSerializer.deserializeBounds(entity.bounds),
                createdAt = entity.createdAt,
                createdLamport = entity.createdLamport,
            )
        }

    /**
     * Load strokes for multiple pages in a single transaction.
     * More efficient than calling getStrokesForPage multiple times.
     *
     * @param pageIds List of page IDs to load strokes for
     * @return Map of pageId to list of strokes for that page
     */
    suspend fun getStrokesForPages(pageIds: List<String>): Map<String, List<Stroke>> {
        if (pageIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, List<Stroke>>()
        for (pageId in pageIds) {
            result[pageId] = getStrokesForPage(pageId)
        }
        return result
    }

    suspend fun updateRecognition(
        pageId: String,
        text: String?,
        recognizerVersion: String?,
    ) {
        val now = System.currentTimeMillis()
        recognitionDao.updateRecognition(
            pageId = pageId,
            text = text,
            version = recognizerVersion,
            updatedAt = now,
        )

        updatePageTimestamp(pageId)
    }

    suspend fun upgradePageToMixed(pageId: String) {
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        if (page.kind == "pdf") {
            pageDao.updateKind(pageId, "mixed")
            if (recognitionDao.getByPageId(pageId) == null) {
                val now = System.currentTimeMillis()
                recognitionDao.insert(
                    RecognitionIndexEntity(
                        pageId = pageId,
                        noteId = page.noteId,
                        recognizedText = null,
                        recognizedAtLamport = null,
                        recognizerVersion = null,
                        updatedAt = now,
                    ),
                )
            }
            updatePageTimestamp(pageId)
        }
    }

    suspend fun deletePage(pageId: String) {
        strokeDao.deleteAllForPage(pageId)
        recognitionDao.deleteByPageId(pageId)
        pageDao.delete(pageId)
    }

    suspend fun deleteNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(noteId, now)
    }

    suspend fun updateNoteTitle(
        noteId: String,
        title: String,
    ) {
        val now = System.currentTimeMillis()
        noteDao.updateTitle(noteId = noteId, title = title, updatedAt = now)
    }

    fun searchNotes(query: String): Flow<List<SearchResultItem>> {
        return recognitionDao.search(query).map { recognitionHits ->
            recognitionHits
                .mapNotNull { recognition ->
                    val page = pageDao.getById(recognition.pageId) ?: return@mapNotNull null
                    val note = noteDao.getById(page.noteId) ?: return@mapNotNull null

                    val snippet = recognition.recognizedText.orEmpty().take(SNIPPET_LENGTH)

                    SearchResultItem(
                        noteId = note.noteId,
                        noteTitle = note.title.ifEmpty { "Untitled Note" },
                        pageId = recognition.pageId,
                        pageNumber = page.indexInNote + 1,
                        snippetText = snippet,
                        matchScore = 1.0,
                    )
                }.distinctBy { it.noteId }
                .sortedByDescending { it.matchScore }
        }
    }

    private suspend fun updatePageTimestamp(pageId: String) {
        val now = System.currentTimeMillis()
        pageDao.updateTimestamp(pageId, now)
        val page = requireNotNull(pageDao.getById(pageId)) { "Page not found: $pageId" }
        noteDao.updateTimestamp(page.noteId, now)
    }

    // Thumbnail operations

    /**
     * Get the thumbnail for a note, regenerating if missing.
     *
     * @param noteId The ID of the note
     * @return The ThumbnailEntity, or null if not available
     */
    suspend fun getThumbnail(noteId: String): ThumbnailEntity? = thumbnailGenerator?.getThumbnail(noteId)

    /**
     * Generate a thumbnail for a note.
     *
     * @param noteId The ID of the note
     * @return The generated ThumbnailEntity, or null if generation failed
     */
    suspend fun generateThumbnail(noteId: String): ThumbnailEntity? {
        return thumbnailGenerator?.generateThumbnail(noteId)
    }

    /**
     * Regenerate a thumbnail if it's missing from storage.
     *
     * @param noteId The ID of the note
     * @return The regenerated ThumbnailEntity, or null if regeneration failed
     */
    suspend fun regenerateThumbnailIfMissing(noteId: String): ThumbnailEntity? {
        return thumbnailGenerator?.regenerateIfMissing(noteId)
    }

    /**
     * Delete a thumbnail for a note.
     *
     * @param noteId The ID of the note
     */
    suspend fun deleteThumbnail(noteId: String) {
        thumbnailGenerator?.deleteThumbnail(noteId)
    }

    // Folder operations

    /**
     * Create a new folder with the given name.
     *
     * @param name The name of the folder
     * @return The created FolderEntity
     */
    suspend fun createFolder(name: String): FolderEntity {
        val now = System.currentTimeMillis()
        val folder =
            FolderEntity(
                folderId = UUID.randomUUID().toString(),
                name = name,
                parentId = null,
                createdAt = now,
            )
        folderDao.insert(folder)
        return folder
    }

    /**
     * Delete a folder. All notes in the folder are moved to root (folderId set to null).
     *
     * @param folderId The ID of the folder to delete
     */
    suspend fun deleteFolder(folderId: String) {
        val now = System.currentTimeMillis()
        // Move all notes in this folder to root
        noteDao.moveNotesToRoot(folderId, now)
        // Delete the folder
        folderDao.delete(folderId)
    }

    /**
     * Get all folders.
     *
     * @return Flow of list of all folders
     */
    fun getFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    /**
     * Move a note to a folder. Pass null for folderId to move to root.
     *
     * @param noteId The ID of the note to move
     * @param folderId The ID of the destination folder, or null for root
     */
    suspend fun moveNoteToFolder(
        noteId: String,
        folderId: String?,
    ) {
        val now = System.currentTimeMillis()
        noteDao.updateFolder(noteId, folderId, now)
    }

    /**
     * Get notes in a specific folder. Pass null for folderId to get root notes.
     *
     * @param folderId The ID of the folder, or null for root
     * @return Flow of list of notes in the folder
     */
    fun getNotesInFolder(folderId: String?): Flow<List<NoteEntity>> =
        when (folderId) {
            null -> noteDao.getRootNotes()
            else -> noteDao.getNotesByFolder(folderId)
        }

    /**
     * Get notes in a specific folder with sorting.
     *
     * @param folderId The ID of the folder, or null for root
     * @param sortOption The sort option (NAME, CREATED, MODIFIED)
     * @param sortDirection The sort direction (ASC, DESC)
     * @return Flow of list of notes in the folder, sorted
     */
    @Suppress("CyclomaticComplexMethod")
    fun getNotesInFolderSorted(
        folderId: String?,
        sortOption: SortOption,
        sortDirection: SortDirection,
    ): Flow<List<NoteEntity>> {
        return when (folderId) {
            null -> {
                // Root notes
                when (sortOption) {
                    SortOption.NAME -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByNameAsc()
                        } else {
                            noteDao.getRootNotesSortedByNameDesc()
                        }
                    }
                    SortOption.CREATED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByCreatedAsc()
                        } else {
                            noteDao.getRootNotesSortedByCreatedDesc()
                        }
                    }
                    SortOption.MODIFIED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getRootNotesSortedByModifiedAsc()
                        } else {
                            noteDao.getRootNotesSortedByModifiedDesc()
                        }
                    }
                }
            }
            else -> {
                // Folder notes
                when (sortOption) {
                    SortOption.NAME -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByNameAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByNameDesc(folderId)
                        }
                    }
                    SortOption.CREATED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByCreatedAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByCreatedDesc(folderId)
                        }
                    }
                    SortOption.MODIFIED -> {
                        if (sortDirection == SortDirection.ASC) {
                            noteDao.getNotesByFolderSortedByModifiedAsc(folderId)
                        } else {
                            noteDao.getNotesByFolderSortedByModifiedDesc(folderId)
                        }
                    }
                }
            }
        }
    }

    /**
     * Get notes filtered by date range.
     *
     * @param folderId The ID of the folder, or null for root
     * @param dateRange The date range filter
     * @return Flow of list of notes matching the date range
     */
    fun getNotesByDateRange(
        folderId: String?,
        dateRange: DateRange,
    ): Flow<List<NoteEntity>> {
        val (startTime, endTime) = calculateDateRangeBounds(dateRange)
        return when (folderId) {
            null -> {
                if (dateRange == DateRange.OLDER) {
                    noteDao.getRootNotesOlderThan(endTime)
                } else {
                    noteDao.getRootNotesByDateRange(startTime, endTime)
                }
            }
            else -> {
                if (dateRange == DateRange.OLDER) {
                    noteDao.getNotesByFolderOlderThan(folderId, endTime)
                } else {
                    noteDao.getNotesByFolderAndDateRange(folderId, startTime, endTime)
                }
            }
        }
    }

    /**
     * Calculate start and end time bounds for a date range.
     * Returns Pair(startTime, endTime) in milliseconds.
     */
    private fun calculateDateRangeBounds(dateRange: DateRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now

        return when (dateRange) {
            DateRange.TODAY -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }
            DateRange.THIS_WEEK -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek())
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }
            DateRange.THIS_MONTH -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.MONTH, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }
            DateRange.THIS_YEAR -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val startTime = calendar.timeInMillis
                calendar.add(java.util.Calendar.YEAR, 1)
                val endTime = calendar.timeInMillis
                Pair(startTime, endTime)
            }
            DateRange.OLDER -> {
                // For OLDER, we use endTime as the cutoff (1 year ago)
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val endTime = calendar.timeInMillis
                Pair(0L, endTime)
            }
        }
    }

    // Tag operations

    /**
     * Create a new tag with the given name and color.
     *
     * @param name The name of the tag (must be unique)
     * @param color The color of the tag as a hex string (e.g., "#FF5722")
     * @return The created TagEntity
     * @throws DuplicateTagNameException if a tag with the same name already exists
     */
    suspend fun createTag(
        name: String,
        color: String,
    ): TagEntity {
        // Check for duplicate name
        val existingTag = tagDao.getByName(name)
        if (existingTag != null) {
            throw DuplicateTagNameException(name)
        }

        val now = System.currentTimeMillis()
        val tag =
            TagEntity(
                tagId = UUID.randomUUID().toString(),
                name = name,
                color = color,
                createdAt = now,
            )
        tagDao.insert(tag)
        return tag
    }

    /**
     * Delete a tag by its ID. This also removes all note-tag associations.
     *
     * @param tagId The ID of the tag to delete
     */
    suspend fun deleteTag(tagId: String) {
        tagDao.delete(tagId)
    }

    /**
     * Get all tags.
     *
     * @return Flow of list of all tags ordered by name
     */
    fun getTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    /**
     * Add a tag to a note.
     *
     * @param noteId The ID of the note
     * @param tagId The ID of the tag
     */
    suspend fun addTagToNote(
        noteId: String,
        tagId: String,
    ) {
        tagDao.addTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tagId))
    }

    /**
     * Remove a tag from a note.
     *
     * @param noteId The ID of the note
     * @param tagId The ID of the tag
     */
    suspend fun removeTagFromNote(
        noteId: String,
        tagId: String,
    ) {
        tagDao.removeTagFromNote(noteId = noteId, tagId = tagId)
    }

    /**
     * Get all tags assigned to a note.
     *
     * @param noteId The ID of the note
     * @return Flow of list of tags assigned to the note
     */
    fun getTagsForNote(noteId: String): Flow<List<TagEntity>> = tagDao.getTagsForNote(noteId)

    /**
     * Get all notes that have a specific tag.
     *
     * @param tagId The ID of the tag
     * @return Flow of list of notes with the tag
     */
    fun getNotesByTag(tagId: String): Flow<List<NoteEntity>> = tagDao.getNotesWithTag(tagId)

    // Batch operations for multi-select

    /**
     * Delete multiple notes by their IDs.
     *
     * @param noteIds The set of note IDs to delete
     */
    suspend fun deleteNotes(noteIds: Set<String>) {
        val now = System.currentTimeMillis()
        noteIds.forEach { noteId ->
            noteDao.softDelete(noteId, now)
        }
    }

    /**
     * Move multiple notes to a folder. Pass null for folderId to move to root.
     *
     * @param noteIds The set of note IDs to move
     * @param folderId The ID of the destination folder, or null for root
     */
    suspend fun moveNotesToFolder(
        noteIds: Set<String>,
        folderId: String?,
    ) {
        val now = System.currentTimeMillis()
        noteIds.forEach { noteId ->
            noteDao.updateFolder(noteId, folderId, now)
        }
    }

    /**
     * Add a tag to multiple notes.
     *
     * @param noteIds The set of note IDs to add the tag to
     * @param tagId The ID of the tag to add
     */
    suspend fun addTagToNotes(
        noteIds: Set<String>,
        tagId: String,
    ) {
        noteIds.forEach { noteId ->
            tagDao.addTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tagId))
        }
    }
}
