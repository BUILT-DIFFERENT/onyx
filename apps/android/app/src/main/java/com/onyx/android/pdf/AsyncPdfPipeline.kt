package com.onyx.android.pdf

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DEFAULT_TILE_REQUEST_BUFFER_CAPACITY = 64

data class PdfTileUpdate(
    val key: PdfTileKey,
    val bitmap: Bitmap,
)

class AsyncPdfPipeline(
    private val renderer: PdfTileRenderEngine,
    private val cache: PdfTileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    maxInFlightRenders: Int = 4,
) {
    private val requestMutex = Mutex()
    private val inFlight = mutableMapOf<PdfTileKey, Job>()
    private val renderSemaphore = Semaphore(maxInFlightRenders.coerceAtLeast(1))
    private val _tileUpdates =
        MutableSharedFlow<PdfTileUpdate>(
            extraBufferCapacity = DEFAULT_TILE_REQUEST_BUFFER_CAPACITY,
        )

    val tileUpdates: SharedFlow<PdfTileUpdate> = _tileUpdates

    suspend fun requestTiles(visibleTiles: List<PdfTileKey>) {
        val visibleSet = visibleTiles.toSet()
        requestMutex.withLock {
            val staleKeys = inFlight.keys.filter { key -> key !in visibleSet }
            staleKeys.forEach { key ->
                inFlight.remove(key)?.cancel()
            }
            for (key in visibleTiles) {
                if (!shouldRequestTile(key)) {
                    continue
                }
                val renderJob =
                    scope.launch {
                        var semaphoreAcquired = false
                        var renderedBitmap: Bitmap? = null
                        try {
                            renderSemaphore.acquire()
                            semaphoreAcquired = true
                            renderedBitmap = renderer.renderTile(key)
                            val cachedBitmap = cache.putTile(key, renderedBitmap)
                            _tileUpdates.tryEmit(PdfTileUpdate(key, cachedBitmap))
                        } catch (cancellation: CancellationException) {
                            val bitmap = renderedBitmap
                            if (
                                bitmap != null &&
                                cache.getTile(key) !== bitmap &&
                                !bitmap.isRecycled
                            ) {
                                bitmap.recycle()
                            }
                            throw cancellation
                        } finally {
                            withContext(NonCancellable) {
                                if (semaphoreAcquired) {
                                    renderSemaphore.release()
                                }
                                requestMutex.withLock {
                                    inFlight.remove(key)
                                }
                            }
                        }
                    }
                inFlight[key] = renderJob
            }
        }
    }

    suspend fun cancelAll() {
        requestMutex.withLock {
            inFlight.values.forEach { job -> job.cancel() }
            inFlight.clear()
        }
    }

    private suspend fun shouldRequestTile(key: PdfTileKey): Boolean {
        val cachedTile = cache.getTile(key)
        val isInFlight = inFlight.containsKey(key)
        return cachedTile == null && !isInFlight
    }
}
