@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.runtime.Composable
import com.onyx.android.ui.editor.EditorMultiPageScaffold
import com.onyx.android.ui.editor.EditorScaffold

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    EditorScaffold(
        topBarState = topBarState,
        toolbarState = toolbarState,
        contentState = contentState,
        transformState = transformState,
    )
}

@Composable
internal fun MultiPageEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    multiPageContentState: MultiPageContentState,
) {
    EditorMultiPageScaffold(
        topBarState = topBarState,
        toolbarState = toolbarState,
        multiPageContentState = multiPageContentState,
    )
}
