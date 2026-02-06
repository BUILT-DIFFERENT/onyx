package com.onyx.android.navigation

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}?pageId={pageId}"

    fun editor(
        noteId: String,
        pageId: String? = null,
    ): String =
        if (pageId.isNullOrBlank()) {
            "editor/$noteId"
        } else {
            "editor/$noteId?pageId=$pageId"
        }
}
