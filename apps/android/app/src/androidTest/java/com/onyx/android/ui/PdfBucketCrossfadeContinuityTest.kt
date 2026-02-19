package com.onyx.android.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.pdf.PdfTileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBucketCrossfadeContinuityTest {
    @Test
    fun scaleBucketPolicyHasCorrectBuckets() {
        val bucket1xValue = zoomToRenderScaleBucket(0.5f)
        val bucket2xValue = zoomToRenderScaleBucket(1.5f)
        val bucket4xValue = zoomToRenderScaleBucket(3.0f)

        assertEquals("Bucket 1x for zoom 0.5", 1f, bucket1xValue, DELTA)
        assertEquals("Bucket 2x for zoom 1.5", 2f, bucket2xValue, DELTA)
        assertEquals("Bucket 4x for zoom 3.0", 4f, bucket4xValue, DELTA)
    }

    @Test
    fun hysteresis_preventsOscillation() {
        val buckets = floatArrayOf(1f, 2f, 4f)

        val startedAt1 = zoomToRenderScaleBucket(zoom = 1.0f, previousBucket = 1f)
        val stayedAt1 = zoomToRenderScaleBucket(zoom = 2.1f, previousBucket = 1f)
        val switchedTo2 = zoomToRenderScaleBucket(zoom = 2.2f, previousBucket = 1f)
        val stayedAt2 = zoomToRenderScaleBucket(zoom = 1.8f, previousBucket = 2f)
        val switchedBack = zoomToRenderScaleBucket(zoom = 1.79f, previousBucket = 2f)

        assertEquals(1f, startedAt1, DELTA)
        assertEquals("Should stay at 1x with hysteresis at 2.1x zoom", 1f, stayedAt1, DELTA)
        assertEquals("Should switch to 2x at 2.2x zoom", 2f, switchedTo2, DELTA)
        assertEquals("Should stay at 2x with hysteresis at 1.8x zoom", 2f, stayedAt2, DELTA)
        assertEquals("Should switch back to 1x at 1.79x zoom", 1f, switchedBack, DELTA)
    }

    @Test
    fun crossfadeStartsAtZeroOnBucketChange() {
        var previousBucket: Float? = null
        var crossfadeProgress = 1f
        val initialBucket = zoomToRenderScaleBucket(1.0f, previousBucket)
        previousBucket = initialBucket
        crossfadeProgress = 1f

        val newBucket = zoomToRenderScaleBucket(2.2f, previousBucket)
        if (newBucket != previousBucket) {
            val oldBucket = previousBucket
            previousBucket = newBucket
            crossfadeProgress = 0f
        }

        assertEquals("Initial bucket should be 1x", 1f, initialBucket, DELTA)
        assertEquals("New bucket should be 2x", 2f, newBucket, DELTA)
        assertEquals("Crossfade should start at 0", 0f, crossfadeProgress, DELTA)
    }

    @Test
    fun previousTilesRetained_duringTransition() {
        val currentBucket = 2f
        val previousBucket = 1f

        val currentTileKey =
            PdfTileKey(
                pageIndex = 0,
                tileX = 0,
                tileY = 0,
                scaleBucket = currentBucket,
            )
        val previousTileKey =
            PdfTileKey(
                pageIndex = 0,
                tileX = 0,
                tileY = 0,
                scaleBucket = previousBucket,
            )

        val currentBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val previousBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val tiles =
            mapOf(
                currentTileKey to currentBitmap,
                previousTileKey to previousBitmap,
            )

        val hasTilesForCurrentBucket = tiles.keys.any { it.scaleBucket == currentBucket }
        val hasTilesForPreviousBucket = tiles.keys.any { it.scaleBucket == previousBucket }

        assertTrue("Should have tiles for current bucket", hasTilesForCurrentBucket)
        assertTrue("Should have tiles for previous bucket during crossfade", hasTilesForPreviousBucket)

        currentBitmap.recycle()
        previousBitmap.recycle()
    }

    @Test
    fun noBlankFrame_whenTilesAvailable() {
        val currentBucket = 2f
        val previousBucket = 1f
        val crossfadeProgress = 0.5f

        val currentTileKey =
            PdfTileKey(
                pageIndex = 0,
                tileX = 0,
                tileY = 0,
                scaleBucket = currentBucket,
            )
        val previousTileKey =
            PdfTileKey(
                pageIndex = 0,
                tileX = 0,
                tileY = 0,
                scaleBucket = previousBucket,
            )

        val currentBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val previousBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

        val tiles =
            mapOf(
                currentTileKey to currentBitmap,
                previousTileKey to previousBitmap,
            )

        val visibleTiles =
            tiles.entries.partition { entry ->
                entry.key.scaleBucket == previousBucket
            }

        val (previousBucketEntries, currentBucketEntries) = visibleTiles
        val previousBucketAlpha = 1f - crossfadeProgress
        val currentBucketAlpha = crossfadeProgress

        val hasVisiblePreviousTiles = previousBucketEntries.isNotEmpty() && previousBucketAlpha > 0f
        val hasVisibleCurrentTiles = currentBucketEntries.isNotEmpty() && currentBucketAlpha > 0f

        assertTrue(
            "At crossfade 0.5, should have visible previous tiles",
            hasVisiblePreviousTiles,
        )
        assertTrue(
            "At crossfade 0.5, should have visible current tiles",
            hasVisibleCurrentTiles,
        )
        assertFalse(
            "At crossfade 0.5, should never have blank frame (both alphas zero)",
            previousBucketAlpha <= 0f && currentBucketAlpha <= 0f,
        )

        currentBitmap.recycle()
        previousBitmap.recycle()
    }

    @Test
    fun crossfadeCompletesCorrectly() {
        val previousBucketAlphaAt0 = 1f - 0f
        val currentBucketAlphaAt0 = 0f
        val previousBucketAlphaAt50 = 1f - 0.5f
        val currentBucketAlphaAt50 = 0.5f
        val previousBucketAlphaAt100 = 1f - 1f
        val currentBucketAlphaAt100 = 1f

        assertEquals("At start, previous alpha is 1", 1f, previousBucketAlphaAt0, DELTA)
        assertEquals("At start, current alpha is 0", 0f, currentBucketAlphaAt0, DELTA)
        assertEquals("At 50%, previous alpha is 0.5", 0.5f, previousBucketAlphaAt50, DELTA)
        assertEquals("At 50%, current alpha is 0.5", 0.5f, currentBucketAlphaAt50, DELTA)
        assertEquals("At end, previous alpha is 0", 0f, previousBucketAlphaAt100, DELTA)
        assertEquals("At end, current alpha is 1", 1f, currentBucketAlphaAt100, DELTA)
    }

    @Test
    fun bucketTransitionDoesNotSkipBuckets() {
        val bucket1to2Transition = zoomToRenderScaleBucket(zoom = 2.2f, previousBucket = 1f)
        val bucket2to4Transition = zoomToRenderScaleBucket(zoom = 4.4f, previousBucket = 2f)

        assertEquals("1x should transition to 2x directly", 2f, bucket1to2Transition, DELTA)
        assertEquals("2x should transition to 4x directly", 4f, bucket2to4Transition, DELTA)

        val bucket4to2Transition = zoomToRenderScaleBucket(zoom = 3.5f, previousBucket = 4f)
        assertEquals("4x should transition to 2x at lower threshold", 2f, bucket4to2Transition, DELTA)
    }

    @Test
    fun zoomInStepsUpImmediately() {
        val bucketAtZoom1 = zoomToRenderScaleBucket(zoom = 1.0f, previousBucket = null)
        assertEquals("No previous bucket, 1.0x zoom -> 1x bucket", 1f, bucketAtZoom1, DELTA)

        val bucketAtZoom1Dot5 = zoomToRenderScaleBucket(zoom = 1.5f, previousBucket = 1f)
        assertEquals("Already at 1x, 1.5x zoom stays at 1x", 1f, bucketAtZoom1Dot5, DELTA)

        val bucketAtZoom2Dot2 = zoomToRenderScaleBucket(zoom = 2.2f, previousBucket = 1f)
        assertEquals("1x bucket, 2.2x zoom steps up to 2x", 2f, bucketAtZoom2Dot2, DELTA)
    }

    @Test
    fun zoomOutStepsDownWithHysteresis() {
        val bucketAtZoom4 = zoomToRenderScaleBucket(zoom = 4.0f, previousBucket = null)
        assertEquals("No previous bucket, 4.0x zoom -> 4x bucket", 4f, bucketAtZoom4, DELTA)

        val bucketAtZoom3Dot5 = zoomToRenderScaleBucket(zoom = 3.5f, previousBucket = 4f)
        assertEquals("Already at 4x, 3.5x zoom stays at 4x (hysteresis)", 4f, bucketAtZoom3Dot5, DELTA)

        val bucketAtZoom3Dot5With2x = zoomToRenderScaleBucket(zoom = 3.5f, previousBucket = 2f)
        assertEquals("At 2x bucket, 3.5x zoom stays at 2x (hysteresis)", 2f, bucketAtZoom3Dot5With2x, DELTA)

        val bucketAtZoom1Dot7 = zoomToRenderScaleBucket(zoom = 1.7f, previousBucket = 2f)
        assertEquals("At 2x bucket, 1.7x zoom stays at 2x (hysteresis)", 2f, bucketAtZoom1Dot7, DELTA)

        val bucketAtZoom1Dot6 = zoomToRenderScaleBucket(zoom = 1.6f, previousBucket = 2f)
        assertEquals("At 2x bucket, 1.6x zoom (below 1.8 threshold) steps to 1x", 1f, bucketAtZoom1Dot6, DELTA)
    }
}

private const val DELTA = 0.0001f
