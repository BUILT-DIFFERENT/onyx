# Spatial Index Stress Fixture

**Date**: 2026-02-17
**Task**: 1.5 Spatial index for selection and large-document hit-testing

---

## Fixture Definition

### Stroke Count

- **Baseline**: 100 strokes (typical note)
- **Stress**: 5,000 strokes (heavy note)
- **Extreme**: 10,000 strokes (stress limit)

### Page Count

- **Single-page mode**: All strokes on one page
- **Multi-page mode**: Strokes distributed across 10 pages (500-1000 strokes per page)

### Stroke Geometry

- **Average stroke length**: 30-50 points
- **Stroke bounds**: 30-50 units wide/tall
- **Distribution**: Grid-based for predictable coverage

---

## Gesture Script (Reproducible Testing)

### Test Scenario 1: Eraser Hit-Test Performance

```
1. Load document with 5000 strokes
2. Position eraser at (50, 50) page coordinates
3. Perform 100 rapid tap-erase gestures across document
4. Measure time per hit-test operation
```

### Test Scenario 2: Lasso Selection Performance

```
1. Load document with 10000 strokes
2. Draw lasso rectangle: (0, 0) to (500, 500)
3. Repeat lasso query 50 times
4. Measure time per lasso candidate retrieval
```

### Test Scenario 3: Undo/Redo Index Consistency

```
1. Load document with 1000 strokes
2. Erase 50 strokes randomly
3. Undo all 50 erasures
4. Verify spatial index contains all 1000 strokes
5. Redo all 50 erasures
6. Verify spatial index contains 950 strokes
```

---

## Automated Test Execution

Run benchmark tests:

```bash
bun run android:test --tests "com.onyx.android.ink.ui.StrokeSpatialIndexBenchmark"
```

Expected results:

- Hit-test speedup: > 2x (5000 strokes)
- Lasso speedup: > 5x (10000 strokes)
- Index build time: < 100ms (10000 strokes)

---

## Performance Targets

| Metric              | Target  | Notes                               |
| ------------------- | ------- | ----------------------------------- |
| Hit-test latency    | < 1ms   | For 5000 strokes at any zoom        |
| Lasso query latency | < 5ms   | For 10000 strokes in 500x500 region |
| Index rebuild time  | < 100ms | On main thread for 10000 strokes    |
| Memory overhead     | < 1MB   | For 10000 strokes                   |

---

## Cell Size Rationale

Chosen cell size: **100 units (points)**

Reasons:

1. Typical stroke length is 30-50 units → most strokes fit in 1-4 cells
2. Page size is typically 600-800 points → 6-8 cells per dimension
3. Larger cells reduce cell count but increase false positives
4. Smaller cells increase memory overhead and cell management cost

Trade-off:

- Cell size 100: Good balance of query speed and memory
- Could be tuned based on actual stroke density measurements
