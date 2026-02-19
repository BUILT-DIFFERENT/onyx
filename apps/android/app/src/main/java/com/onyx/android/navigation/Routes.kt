package com.onyx.android.navigation

import androidx.compose.ui.geometry.Rect
import com.onyx.android.data.repository.SearchResult

object Routes {
    const val HOME = "home"
    const val EDITOR =
        "editor/{noteId}?pageId={pageId}&pageIndex={pageIndex}" +
            "&highlightLeft={highlightLeft}&highlightTop={highlightTop}" +
            "&highlightRight={highlightRight}&highlightBottom={highlightBottom}"
    const val DEVELOPER_FLAGS = "developer_flags"

    fun editor(
        noteId: String,
        pageId: String? = null,
        pageIndex: Int? = null,
        highlightBounds: Rect? = null,
    ): String =
        buildString {
            append("editor/")
            append(noteId)
            if (!pageId.isNullOrBlank()) {
                append("?pageId=")
                append(pageId)
            } else {
                append("?pageId=")
            }
            append("&pageIndex=")
            append(pageIndex?.toString().orEmpty())
            append("&highlightLeft=")
            append(highlightBounds?.left?.toString().orEmpty())
            append("&highlightTop=")
            append(highlightBounds?.top?.toString().orEmpty())
            append("&highlightRight=")
            append(highlightBounds?.right?.toString().orEmpty())
            append("&highlightBottom=")
            append(highlightBounds?.bottom?.toString().orEmpty())
        }

    fun editorForSearchResult(result: SearchResult): String =
        editor(
            noteId = result.noteId,
            pageId = result.pageId,
            pageIndex = result.pageIndex,
            highlightBounds = result.bounds,
        )
}
