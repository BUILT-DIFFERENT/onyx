@file:Suppress("FunctionName", "MagicNumber", "LongParameterList")

package com.onyx.android.ui.editor.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun RowScope.NoteTitleEditor(
    noteTitle: String,
    titleDraft: String,
    isEditing: Boolean,
    isEditingEnabled: Boolean,
    onTitleDraftChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCommit: () -> Unit,
) {
    if (isEditing && isEditingEnabled) {
        OutlinedTextField(
            value = titleDraft,
            onValueChange = onTitleDraftChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(TITLE_INPUT_TEST_TAG)
                    .semantics { contentDescription = "Note title input" },
            textStyle = MaterialTheme.typography.titleSmall,
        )
    } else {
        val displayTitle = noteTitle.ifBlank { "Untitled note" }
        TextButton(
            onClick = onStartEditing,
            enabled = isEditingEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Note title" },
        ) {
            Text(
                text = displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
            )
        }
    }
}
