@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.onyx.android.ui.editor.EditorMultiPageScaffold
import com.onyx.android.ui.editor.EditorScaffold

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
    snackbarHostState: SnackbarHostState,
) {
    EditorScaffold(
        topBarState = topBarState,
        toolbarState = toolbarState,
        contentState = contentState,
        transformState = transformState,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun MultiPageEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    multiPageContentState: MultiPageContentState,
    snackbarHostState: SnackbarHostState,
) {
    EditorMultiPageScaffold(
        topBarState = topBarState,
        toolbarState = toolbarState,
        multiPageContentState = multiPageContentState,
        snackbarHostState = snackbarHostState,
    )
}
