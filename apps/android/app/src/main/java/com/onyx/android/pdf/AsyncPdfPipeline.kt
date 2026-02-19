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
private const val DEFAULT_MAX_QUEUE_SIZE = 24
private const val QUEUE_DEPTH_LOG_BUCKET_SIZE = 4
private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
private const val TAG = "AsyncPdfPipeline"

data class PdfTileUpdate(
    val key: PdfTileKey,
    val tile: ValidatingTile,
    val requestStartNanos: Long,
)

data class PdfPipelineConfig(
    val maxInFlightRenders: Int = 4,
    val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
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
    private val inFlightOrder = ArrayDeque<PdfTileKey>()
    private val requestTimestamps = mutableMapOf<PdfTileKey, Long>()
    private val renderSemaphore = Semaphore(config.maxInFlightRenders.coerceAtLeast(1))
    private var lastQueueDepthLogBucket = -1
    private val _tileUpdates =
        MutableSharedFlow<PdfTileUpdate>(
            extraBufferCapacity = DEFAULT_TILE_REQUEST_BUFFER_CAPACITY,
        )

    val tileUpdates: SharedFlow<PdfTileUpdate> = _tileUpdates
    val prefetchRadius: Int get() = config.prefetchRadius

    @Suppress("LongMethod", "LoopWithTooManyJumpStatements", "CyclomaticComplexMethod")
    suspend fun requestTiles(visibleTiles: List<PdfTileKey>) {
        val visibleSet = visibleTiles.toSet()
        requestMutex.withLock {
            val staleKeys = inFlight.keys.filter { key -> key !in visibleSet }
            cancelInFlightKeysLocked(staleKeys, reason = "viewport-shift")
            logQueueDepthLocked()
            assertQueueInvariantsLocked()

            for (key in visibleTiles) {
                if (!shouldRequestTile(key)) {
                    continue
                }
                var queueTrimmed = false
                // Invariant: keep queue bounded by evicting oldest in-flight request first.
                // New scheduling wave always takes priority over older queued work.
                while (inFlight.size >= config.maxQueueSize) {
                    val trimmedKey = inFlightOrder.removeFirstOrNull() ?: break
                    val cancelled = cancelInFlightKeysLocked(listOf(trimmedKey), reason = "queue-pressure")
                    if (cancelled == 0) {
                        break
                    }
                    queueTrimmed = true
                }

                if (inFlight.size >= config.maxQueueSize) {
                    logQueueFull(
                        inFlightSize = inFlight.size,
                        maxQueueSize = config.maxQueueSize,
                        key = key,
                    )
                    break
                }
                if (queueTrimmed) {
                    logQueueDepthLocked()
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
                            logTileVisibleLatency(key = key, startNanos = startNanos)
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
                                    inFlightOrder.remove(key)
                                    requestTimestamps.remove(key)
                                    logQueueDepthLocked()
                                    assertQueueInvariantsLocked()
                                }
                            }
                        }
                    }
                inFlight[key] = renderJob
                inFlightOrder.remove(key)
                inFlightOrder.addLast(key)
                logQueueDepthLocked()
                assertQueueInvariantsLocked()
            }
        }
    }

    suspend fun cancelAll() {
        requestMutex.withLock {
            inFlight.values.forEach { job -> job.cancel() }
            inFlight.clear()
            inFlightOrder.clear()
            requestTimestamps.clear()
            logQueueDepthLocked()
            assertQueueInvariantsLocked()
        }
    }

    private suspend fun shouldRequestTile(key: PdfTileKey): Boolean {
        val cachedTile = cache.getTile(key)
        val isInFlight = inFlight.containsKey(key)
        return cachedTile == null && !isInFlight
    }

    private fun cancelInFlightKeysLocked(
        keys: List<PdfTileKey>,
        reason: String,
    ): Int {
        if (keys.isEmpty()) {
            return 0
        }
        var cancelledCount = 0
        keys.forEach { key ->
            val job = inFlight.remove(key) ?: return@forEach
            inFlightOrder.remove(key)
            requestTimestamps.remove(key)
            job.cancel()
            cancelledCount += 1
        }
        if (cancelledCount > 0) {
            PerfInstrumentation.logTileStaleCancel(cancelledCount)
            logQueueCancellation(reason = reason, count = cancelledCount, queueDepth = inFlight.size)
            logQueueDepthLocked()
        }
        return cancelledCount
    }

    private fun logQueueDepthLocked() {
        val depth = inFlight.size
        PerfInstrumentation.logTileQueueDepth(depth)
        val depthBucket = depth / QUEUE_DEPTH_LOG_BUCKET_SIZE
        if (depthBucket != lastQueueDepthLogBucket || depth >= config.maxQueueSize) {
            lastQueueDepthLogBucket = depthBucket
            runCatching {
                Log.d(
                    TAG,
                    "Queue depth=$depth maxQueueSize=${config.maxQueueSize}",
                )
            }
        }
    }

    private fun assertQueueInvariantsLocked() {
        // Cancellation invariants:
        // 1) queue must stay bounded,
        // 2) ordering index must track active jobs 1:1,
        // 3) timestamps exist only for active in-flight jobs.
        check(inFlight.size <= config.maxQueueSize) {
            "Queue overflow: inFlight=${inFlight.size}, maxQueueSize=${config.maxQueueSize}"
        }
        check(inFlightOrder.size == inFlight.size) {
            "Queue ordering mismatch: inFlightOrder=${inFlightOrder.size}, inFlight=${inFlight.size}"
        }
        check(requestTimestamps.keys.all { key -> key in inFlight }) {
            "Request timestamp exists without active in-flight job"
        }
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

private fun logQueueCancellation(
    reason: String,
    count: Int,
    queueDepth: Int,
) {
    runCatching {
        Log.i(
            TAG,
            "Cancelled $count stale request(s) due to $reason, queueDepth=$queueDepth",
        )
    }
}

private fun logTileVisibleLatency(
    key: PdfTileKey,
    startNanos: Long,
) {
    val elapsedMs = (System.nanoTime() - startNanos) / NANOSECONDS_PER_MILLISECOND
    runCatching {
        Log.d(
            TAG,
            "Tile visible latency ${elapsedMs}ms key=$key",
        )
    }
}
