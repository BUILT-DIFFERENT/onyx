package com.onyx.android.pdf

import android.graphics.Bitmap
import android.util.Log
import com.onyx.android.ink.perf.PerfInstrumentation
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
private const val MAX_IN_FLIGHT_QUEUE_SIZE = 32
private const val TAG = "AsyncPdfPipeline"

data class PdfTileUpdate(
    val key: PdfTileKey,
    val tile: ValidatingTile,
    val requestStartNanos: Long,
)

data class PdfPipelineConfig(
    val maxInFlightRenders: Int = 4,
    val maxQueueSize: Int = MAX_IN_FLIGHT_QUEUE_SIZE,
    val prefetchRadius: Int = 1,
) {
    init {
        require(maxInFlightRenders >= 1) { "maxInFlightRenders must be at least 1" }
        require(maxQueueSize >= 1) { "maxQueueSize must be at least 1" }
        require(prefetchRadius >= 0) { "prefetchRadius must be non-negative" }
    }

    companion object {
        val DEFAULT = PdfPipelineConfig()
    }
}

class AsyncPdfPipeline(
    private val renderer: PdfTileRenderEngine,
    private val cache: PdfTileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val config: PdfPipelineConfig = PdfPipelineConfig.DEFAULT,
) {
    constructor(
        renderer: PdfTileRenderEngine,
        cache: PdfTileStore,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        maxInFlightRenders: Int = 4,
    ) : this(
        renderer = renderer,
        cache = cache,
        scope = scope,
        config = PdfPipelineConfig(maxInFlightRenders = maxInFlightRenders),
    )

    private val requestMutex = Mutex()
    private val inFlight = mutableMapOf<PdfTileKey, Job>()
    private val requestTimestamps = mutableMapOf<PdfTileKey, Long>()
    private val renderSemaphore = Semaphore(config.maxInFlightRenders.coerceAtLeast(1))
    private val _tileUpdates =
        MutableSharedFlow<PdfTileUpdate>(
            extraBufferCapacity = DEFAULT_TILE_REQUEST_BUFFER_CAPACITY,
        )

    val tileUpdates: SharedFlow<PdfTileUpdate> = _tileUpdates
    val prefetchRadius: Int get() = config.prefetchRadius

    @Suppress("LongMethod", "LoopWithTooManyJumpStatements")
    suspend fun requestTiles(visibleTiles: List<PdfTileKey>) {
        val visibleSet = visibleTiles.toSet()
        requestMutex.withLock {
            val staleKeys = inFlight.keys.filter { key -> key !in visibleSet }
            if (staleKeys.isNotEmpty()) {
                staleKeys.forEach { key ->
                    inFlight.remove(key)?.cancel()
                    requestTimestamps.remove(key)
                }
                PerfInstrumentation.logTileStaleCancel(staleKeys.size)
            }
            PerfInstrumentation.logTileQueueDepth(inFlight.size)

            for (key in visibleTiles) {
                if (!shouldRequestTile(key)) {
                    continue
                }
                if (inFlight.size >= config.maxQueueSize) {
                    logQueueFull(
                        inFlightSize = inFlight.size,
                        maxQueueSize = config.maxQueueSize,
                        key = key,
                    )
                    break
                }
                val requestStartNanos = System.nanoTime()
                requestTimestamps[key] = requestStartNanos
                val renderJob =
                    scope.launch {
                        var semaphoreAcquired = false
                        var renderedBitmap: Bitmap? = null
                        try {
                            renderSemaphore.acquire()
                            semaphoreAcquired = true
                            renderedBitmap = renderer.renderTile(key)
                            val cachedBitmap = cache.putTile(key, renderedBitmap)
                            val startNanos =
                                requestMutex.withLock {
                                    requestTimestamps.remove(key) ?: requestStartNanos
                                }
                            PerfInstrumentation.logTileVisibleLatency(startNanos)
                            _tileUpdates.tryEmit(PdfTileUpdate(key, cachedBitmap, startNanos))
                        } catch (cancellation: CancellationException) {
                            val bitmap = renderedBitmap
                            if (
                                bitmap != null &&
                                cache.getTile(key)?.bitmap !== bitmap &&
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
            requestTimestamps.clear()
        }
    }

    private suspend fun shouldRequestTile(key: PdfTileKey): Boolean {
        val cachedTile = cache.getTile(key)
        val isInFlight = inFlight.containsKey(key)
        return cachedTile == null && !isInFlight
    }
}

private fun logQueueFull(
    inFlightSize: Int,
    maxQueueSize: Int,
    key: PdfTileKey,
) {
    runCatching {
        Log.w(
            TAG,
            "Queue full ($inFlightSize/$maxQueueSize), dropping request for $key",
        )
    }
}
