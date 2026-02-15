@file:Suppress("FunctionName", "LongMethod", "TooManyFunctions")

package com.onyx.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.android.pdf.OutlineItem

private const val OUTLINE_INDENT_WIDTH_DP = 16
private const val OUTLINE_ITEM_HEIGHT_DP = 48
private const val OUTLINE_ICON_SIZE_DP = 24

/**
 * A bottom sheet that displays a PDF's table of contents (outline/bookmarks).
 * Supports nested bookmarks with expandable/collapsible items.
 *
 * @param outlineItems The list of top-level outline items to display
 * @param onOutlineItemClick Called when an outline item is clicked, receives the item
 * @param onDismiss Called when the sheet should be dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOutlineSheet(
    outlineItems: List<OutlineItem>,
    onOutlineItemClick: (OutlineItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(OUTLINE_ITEM_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Table of Contents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            if (outlineItems.isEmpty()) {
                // Empty state
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No bookmarks available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This PDF does not have a table of contents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                ) {
                    items(
                        items = outlineItems,
                        key = { item -> "${item.title}-${item.pageIndex}" },
                    ) { item ->
                        OutlineItemRow(
                            item = item,
                            depth = 0,
                            onItemClick = onOutlineItemClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row in the outline list, supporting nested children.
 */
@Composable
private fun OutlineItemRow(
    item: OutlineItem,
    depth: Int,
    onItemClick: (OutlineItem) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val hasChildren = item.children.isNotEmpty()
    val indentModifier = Modifier.padding(start = (depth * OUTLINE_INDENT_WIDTH_DP).dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(OUTLINE_ITEM_HEIGHT_DP.dp)
                    .then(indentModifier)
                    .clickable(enabled = item.pageIndex >= 0) { onItemClick(item) }
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand/collapse button for items with children
            if (hasChildren) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(OUTLINE_ICON_SIZE_DP.dp),
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(OUTLINE_ICON_SIZE_DP.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title
            Text(
                text = item.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (item.pageIndex >= 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.weight(1f)
                        .semantics { contentDescription = item.title.ifBlank { "Untitled bookmark" } },
            )

            // Page number
            if (item.pageIndex >= 0) {
                Text(
                    text = "${item.pageIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // Children
        if (hasChildren) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    item.children.forEach { child ->
                        OutlineItemRow(
                            item = child,
                            depth = depth + 1,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
        }
    }
}
