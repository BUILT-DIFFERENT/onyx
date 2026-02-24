@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EmptyNotesMessage(modifier: Modifier = Modifier) {
    Text(
        text = "No notes yet. Tap + to create one.",
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SharedNotesPlaceholderMessage(modifier: Modifier = Modifier) {
    Text(
        text = "No shared notes yet. Shared sync metadata is still in progress.",
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun RecentsNotesEmptyMessage(modifier: Modifier = Modifier) {
    Text(
        text = "No recent notes yet. Open a note and it will appear here.",
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
