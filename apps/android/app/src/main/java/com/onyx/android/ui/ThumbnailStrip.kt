@file:Suppress("FunctionName", "MagicNumber", "LongMethod", "UNUSED_PARAMETER")

package com.onyx.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

private val THUMBNAIL_STRIP_BACKGROUND = Color(0xFF1A1F2E)
private val THUMBNAIL_STRIP_ITEM_BACKGROUND = Color(0xFF2B3144)
private val THUMBNAIL_STRIP_SELECTED_BORDER = Color(0xFF136CC5)
private val THUMBNAIL_STRIP_UNSELECTED_BORDER = Color(0xFF3B435B)
private const val THUMBNAIL_STRIP_HEIGHT_DP = 80
private const val THUMBNAIL_WIDTH_DP = 60
private const val THUMBNAIL_PADDING_DP = 4
private const val THUMBNAIL_CORNER_RADIUS_DP = 4
private const val THUMBNAIL_STRIP_VERTICAL_PADDING_DP = 8
private const val THUMBNAIL_STRIP_HORIZONTAL_PADDING_DP = 8
private const val THUMBNAIL_SPACING_DP = 8
private const val THUMBNAIL_SELECTED_BORDER_WIDTH_DP = 2
private const val THUMBNAIL_UNSELECTED_BORDER_WIDTH_DP = 1

/**
 * Thumbnail item data for the strip.
 */
data class ThumbnailItem(
    val pageIndex: Int,
    val aspectRatio: Float,
)

/**
 * A horizontal scrollable strip of page thumbnails for navigation.
 *
 * @param thumbnails List of thumbnail items to display
 * @param currentPageIndex The currently selected page index (0-based)
 * @param onPageSelected Callback when a thumbnail is tapped
 * @param modifier Modifier for the strip
 */
@Composable
fun ThumbnailStrip(
    thumbnails: List<ThumbnailItem>,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    if (thumbnails.isEmpty()) return

    val lazyListState = rememberLazyListState()

    // Scroll to current page when it changes
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex >= 0 && currentPageIndex < thumbnails.size) {
            lazyListState.animateScrollToItem(currentPageIndex)
        }
    }

    Surface(
        modifier =
            modifier
                .height(THUMBNAIL_STRIP_HEIGHT_DP.dp)
                .semantics { contentDescription = "Page thumbnails" },
        color = THUMBNAIL_STRIP_BACKGROUND,
    ) {
        LazyRow(
            state = lazyListState,
            modifier =
                Modifier.padding(
                    vertical = THUMBNAIL_STRIP_VERTICAL_PADDING_DP.dp,
                ),
            contentPadding =
                PaddingValues(
                    horizontal = THUMBNAIL_STRIP_HORIZONTAL_PADDING_DP.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING_DP.dp),
        ) {
            items(
                items = thumbnails,
                key = { it.pageIndex },
            ) { thumbnail ->
                ThumbnailItem(
                    thumbnail = thumbnail,
                    isSelected = thumbnail.pageIndex == currentPageIndex,
                    loadThumbnail = loadThumbnail,
                    onClick = { onPageSelected(thumbnail.pageIndex) },
                )
            }
        }
    }
}

/**
 * Individual thumbnail item in the strip.
 */
@Composable
private fun ThumbnailItem(
    thumbnail: ThumbnailItem,
    isSelected: Boolean,
    loadThumbnail: suspend (Int) -> Bitmap?,
    onClick: () -> Unit,
) {
    val borderWidth = if (isSelected) THUMBNAIL_SELECTED_BORDER_WIDTH_DP.dp else THUMBNAIL_UNSELECTED_BORDER_WIDTH_DP.dp
    val borderColor = if (isSelected) THUMBNAIL_STRIP_SELECTED_BORDER else THUMBNAIL_STRIP_UNSELECTED_BORDER

    val thumbnailHeight = THUMBNAIL_STRIP_HEIGHT_DP - 2 * THUMBNAIL_STRIP_VERTICAL_PADDING_DP
    val thumbnailWidth = (thumbnailHeight / thumbnail.aspectRatio).coerceAtLeast(30f)

    val bitmap by
        produceState<Bitmap?>(initialValue = null, key1 = thumbnail.pageIndex) {
            value = loadThumbnail(thumbnail.pageIndex)
        }

    Box(
        modifier =
            Modifier
                .width(thumbnailWidth.dp)
                .height(thumbnailHeight.dp)
                .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp))
                .background(THUMBNAIL_STRIP_ITEM_BACKGROUND)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp),
                ).clickable(onClick = onClick)
                .semantics {
                    contentDescription = "Page ${thumbnail.pageIndex + 1}"
                }.padding(THUMBNAIL_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { imageBitmap ->
            if (!imageBitmap.isRecycled) {
                Image(
                    bitmap = imageBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } ?: run {
            // Placeholder when no bitmap is available
            Text(
                text = "${thumbnail.pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Calculate thumbnail dimensions based on page size.
 * Renders at 10% scale of original page.
 *
 * @param pageHeight Original page height in points
 * @param targetHeightDp Target height for the thumbnail in DP
 * @return Pair of (width, height) in DP
 */
fun calculateThumbnailDimensions(
    pageHeight: Float,
    targetHeightDp: Float = (THUMBNAIL_STRIP_HEIGHT_DP - 2 * THUMBNAIL_STRIP_VERTICAL_PADDING_DP).toFloat(),
): Pair<Float, Float> {
    val height = targetHeightDp
    return Pair(height, height)
}

/**
 * Calculate the render scale for a thumbnail.
 * Uses 10% of the original page size.
 */
fun calculateThumbnailRenderScale(
    pageWidth: Float,
    pageHeight: Float,
    targetHeightPx: Int,
): Float {
    val pageHeightCoerced = pageHeight.coerceAtLeast(1f)
    return targetHeightPx / pageHeightCoerced
}
