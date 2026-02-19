# PDF Tile Scheduler Baseline

**Task**: 2.2 Tile scheduler and viewport-driven frame-aligned requests
**Date**: 2026-02-17

---

## Before (Issues Identified)

### 1. Direct Gesture Event Triggering

Tile requests were triggered directly via `LaunchedEffect` keys on every `viewTransform` change:

```kotlin
// BEFORE: Triggers on every pan/zoom change (gesture storm)
LaunchedEffect(
    viewTransform.zoom,
    viewTransform.panX,
    viewTransform.panY,
    viewportSize,
    scaleBucket,
) {
    activePipeline.requestTiles(...)
}
```

This caused request storms during flings, with potentially hundreds of requests per second.

### 2. Unbounded In-Flight Queue

The `inFlight` map had no size limit:

- Only bounded by `maxInFlightRenders` semaphore (concurrent renders)
- Queue could grow unbounded if requests arrived faster than renders completed
- Memory pressure during rapid scrolling

### 3. Hardcoded Prefetch Radius

```kotlin
private const val PDF_TILE_PREFETCH_DISTANCE = 1  // Not configurable
```

### 4. Limited Metrics

Only queue depth was logged:

```kotlin
PerfInstrumentation.logTileQueueDepth(inFlight.size)
```

Missing:

- Stale cancellation count
- Tile-visible latency (request to draw)

---

## After (Changes Made)

### 1. Frame-Aligned Request Triggering

Using `snapshotFlow` with `debounce(16ms)` for frame alignment:

```kotlin
@OptIn(FlowPreview::class)
LaunchedEffect(pipeline, tileCache, isPdfPage, currentPage?.pageId) {
    snapshotFlow { Triple(viewTransform, viewportSize, scaleBucket) }
        .debounce(TILE_REQUEST_FRAME_DEBOUNCE_MS)  // 16ms = 1 frame
        .distinctUntilChanged()
        .collect { (transform, size, bucket) ->
            // Request tiles once per frame max
        }
}
```

**Benefits**:

- Batches viewport changes into single request per frame
- Prevents request storms during gesture events
- 16ms debounce aligns with display refresh rate

### 2. Bounded Request Queue

```kotlin
data class PdfPipelineConfig(
    val maxInFlightRenders: Int = 4,
    val maxQueueSize: Int = 32,  // Explicit bound
    val prefetchRadius: Int = 1,
)

// In requestTiles():
if (inFlight.size >= config.maxQueueSize) {
    Log.w(TAG, "Queue full (${inFlight.size}/${config.maxQueueSize}), dropping request")
    break  // Stop accepting new requests
}
```

**Invariants**:

- Queue depth never exceeds `maxQueueSize`
- New requests rejected when queue full (not queued indefinitely)
- Stale requests cancelled immediately on viewport change

### 3. Configurable Prefetch Radius

```kotlin
val pipelineConfig = remember {
    PdfPipelineConfig(
        maxInFlightRenders = 4,
        maxQueueSize = 32,
        prefetchRadius = PDF_TILE_PREFETCH_DISTANCE_DEFAULT,  // Configurable
    )
}

// Pipeline exposes prefetch radius
val prefetchDistance = activePipeline.prefetchRadius
val requestedRange = range.withPrefetch(prefetchDistance).clampedToPage(...)
```

### 4. Enhanced Metrics

**New metrics added**:

| Metric          | Method                    | Description                               |
| --------------- | ------------------------- | ----------------------------------------- |
| Queue depth     | `logTileQueueDepth()`     | Current in-flight request count           |
| Stale cancel    | `logTileStaleCancel()`    | Requests cancelled due to viewport change |
| Visible latency | `logTileVisibleLatency()` | Time from request to tile visible         |

```kotlin
// In PerfInstrumentation.kt
fun logTileStaleCancel(count: Int)
fun logTileVisibleLatency(requestStartNanos: Long)
fun getTileStaleCancelCount(): Int
fun getTileVisibleLatencyStats(): TileStats
```

---

## Queue Configuration Summary

| Parameter            | Default | Purpose                                 |
| -------------------- | ------- | --------------------------------------- |
| `maxInFlightRenders` | 4       | Concurrent render jobs (semaphore)      |
| `maxQueueSize`       | 32      | Total in-flight requests (memory bound) |
| `prefetchRadius`     | 1       | Tiles beyond viewport to prefetch       |

---

## Metrics Added

### Queue Depth Tracking

```kotlin
PerfInstrumentation.logTileQueueDepth(inFlight.size)  // Per request batch
```

### Stale Cancellation Count

```kotlin
val staleKeys = inFlight.keys.filter { key -> key !in visibleSet }
if (staleKeys.isNotEmpty()) {
    staleKeys.forEach { inFlight.remove(it)?.cancel() }
    PerfInstrumentation.logTileStaleCancel(staleKeys.size)
}
```

### Tile-Visible Latency

```kotlin
val requestStartNanos = System.nanoTime()
requestTimestamps[key] = requestStartNanos

// On tile complete:
PerfInstrumentation.logTileVisibleLatency(requestStartNanos)
```

---

## Tests Added

| Test                                      | Purpose                        |
| ----------------------------------------- | ------------------------------ |
| `requestTiles respects max queue size`    | Verify bounded queue behavior  |
| `pipeline config exposes prefetch radius` | Verify config parameter        |
| `pipeline uses config prefetch radius`    | Verify prefetch radius is used |

---

## Verification Checklist

- [x] Frame-aligned request triggering (debounce 16ms)
- [x] Bounded queue with explicit max size
- [x] Stale cancellation on viewport change
- [x] Configurable prefetch radius
- [x] Metrics: queue depth, stale-cancel, tile-visible latency
- [x] `bun run android:test` passes (209 tests)
- [ ] `bun run android:lint` - Pre-existing detekt/ktlint issues (not from this task)
- [ ] Device verification: stress scroll maintains bounded queue

---

## Expected Behavior During Stress Scroll

1. Viewport changes batched at 16ms intervals
2. Stale requests cancelled immediately
3. Queue depth bounded at 32 max
4. No memory pressure from unbounded request accumulation
5. Tile-visible latency logged for performance analysis

---

## Log Output Examples

```
D/OnyxPerf: tile_queue_depth=4
D/OnyxPerf: tile_stale_cancel=3 total=15
D/OnyxPerf: tile_visible_latency_ms=45.23
D/AsyncPdfPipeline: Queue full (32/32), dropping request for PdfTileKey(...)
```
