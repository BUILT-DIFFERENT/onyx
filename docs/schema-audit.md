# Schema Audit: Android Entity ↔ V0 API Contract Alignment

**Purpose**: Verify that Android Room entities align with the v0 API contract defined in `V0-api.md` to ensure sync compatibility.

**Date**: 2026-02-04  
**Milestone**: A (Offline Editor)  
**Status**: ✅ ALIGNED

---

## Summary

All Android entities are **sync-compatible** with the v0 API contract. Key alignments:

- ✅ All IDs are String (UUID format)
- ✅ All timestamps are Long (Unix milliseconds)
- ✅ Page kinds match API: "ink", "pdf", "mixed" (with "infinite" support available)
- ✅ Geometry uses "fixed" | "infinite" with pt units
- ✅ StrokePoint fields match API spec: `t`, `p`, `tx`, `ty`, `r` (not verbose names)
- ✅ StrokeStyle fields align with API: tool, color, baseWidth, minWidthFactor, maxWidthFactor
- ✅ Tool enum serializes to "pen" | "highlighter" | "eraser" strings
- ✅ Bounds use {x, y, w, h} format (not x0/y0/x1/y1)

---

## Entity-by-Entity Verification

### 1. NoteEntity

**File**: `apps/android/app/src/main/java/com/onyx/android/data/entity/NoteEntity.kt`

| Field         | Type          | V0 API Reference                             | Status |
| ------------- | ------------- | -------------------------------------------- | ------ |
| `noteId`      | String (UUID) | `V0-api.md:35` (NoteId type)                 | ✅     |
| `ownerUserId` | String        | `V0-api.md:77` (NoteMeta.ownerUserId)        | ✅     |
| `title`       | String        | `V0-api.md:78` (NoteMeta.title)              | ✅     |
| `createdAt`   | Long          | `V0-api.md:79` (NoteMeta.createdAt: UnixMs)  | ✅     |
| `updatedAt`   | Long          | `V0-api.md:80` (NoteMeta.updatedAt: UnixMs)  | ✅     |
| `deletedAt`   | Long?         | `V0-api.md:81` (NoteMeta.deletedAt?: UnixMs) | ✅     |

**Missing fields (not needed in offline v0)**:

- `previewThumbAssetId`, `previewPageAssetId`, `previewUpdatedAt` — Preview generation handled server-side
- `hasPublicLink`, `shareCount` — Sharing metadata computed by Convex

**Verdict**: ✅ Aligned. All core fields match. Preview/sharing fields intentionally omitted for offline-first client.

---

### 2. PageEntity

**File**: `apps/android/app/src/main/java/com/onyx/android/data/entity/PageEntity.kt`

| Field               | Type          | V0 API Reference                                 | Status |
| ------------------- | ------------- | ------------------------------------------------ | ------ |
| `pageId`            | String (UUID) | `V0-api.md:36` (PageId type)                     | ✅     |
| `noteId`            | String (UUID) | `V0-api.md:95` (PageMeta.noteId)                 | ✅     |
| `kind`              | String        | `V0-api.md:96` (PageMeta.kind: NoteKind)         | ✅     |
| `geometryKind`      | String        | `V0-api.md:49-51` (PageGeometry.kind)            | ✅     |
| `width`             | Float         | `V0-api.md:50` (PageGeometry.width)              | ✅     |
| `height`            | Float         | `V0-api.md:50` (PageGeometry.height)             | ✅     |
| `unit`              | String        | `V0-api.md:50` (PageGeometry.unit: "pt" \| "px") | ✅     |
| `pdfAssetId`        | String?       | `V0-api.md:97` (PageMeta.pdfAssetId?)            | ✅     |
| `pdfPageNo`         | Int?          | `V0-api.md:98` (PageMeta.pdfPageNo?)             | ✅     |
| `contentLamportMax` | Long          | `V0-api.md:103` (PageMeta.contentLamportMax)     | ✅     |
| `updatedAt`         | Long          | `V0-api.md:100` (PageMeta.updatedAt: UnixMs)     | ✅     |

**Additional field (not in API)**:

- `indexInNote: Int` — Local sorting/ordering field. Does not conflict with sync (not transmitted).

**Kind Values Verification**:

- API specifies: `"ink" | "pdf" | "mixed" | "infinite"`
- Implementation uses: `"ink"`, `"pdf"`, `"mixed"` (stored as strings)
- ✅ No legacy "page" value used

**GeometryKind Values Verification**:

- API specifies: `"fixed" | "infinite"`
- Implementation stores: `"fixed"` for PDF/ink pages
- ✅ Aligned

**Missing field (optional in v0)**:

- `latestCommitId` — Snapshot pointer, managed by sync layer

**Verdict**: ✅ Aligned. All sync-critical fields match. `indexInNote` is local-only metadata.

---

### 3. StrokeEntity

**File**: `apps/android/app/src/main/java/com/onyx/android/data/entity/StrokeEntity.kt`

| Field            | Type          | V0 API Reference                           | Status |
| ---------------- | ------------- | ------------------------------------------ | ------ |
| `strokeId`       | String (UUID) | `V0-api.md:149` (StrokeAdd.strokeId)       | ✅     |
| `pageId`         | String (UUID) | FK to PageEntity                           | ✅     |
| `strokeData`     | ByteArray     | Serialized points (see StrokePoint)        | ✅     |
| `style`          | String (JSON) | `V0-api.md:152` (StrokeAdd.style)          | ✅     |
| `bounds`         | String (JSON) | `V0-api.md:153` (StrokeAdd.bounds)         | ✅     |
| `createdAt`      | Long          | Unix ms timestamp                          | ✅     |
| `createdLamport` | Long          | `V0-api.md:150` (StrokeAdd.createdLamport) | ✅     |

**Bounds Format Verification**:

- API specifies: `{ x: number; y: number; w: number; h: number }`
- Implementation: `StrokeBounds(x: Float, y: Float, w: Float, h: Float)` serialized to JSON
- ✅ Uses {x, y, w, h}, not {x0, y0, x1, y1}

**Style Format Verification** (see StrokeStyle model below):

- Matches `V0-api.md:127-134` exactly

**Verdict**: ✅ Aligned. Stroke metadata matches API contract.

---

### 4. StrokePoint Model

**File**: `apps/android/app/src/main/java/com/onyx/android/ink/model/StrokePoint.kt`

| Field | Type   | V0 API Reference                    | Status |
| ----- | ------ | ----------------------------------- | ------ |
| `x`   | Float  | `V0-api.md:137` (Point.x: number)   | ✅     |
| `y`   | Float  | `V0-api.md:138` (Point.y: number)   | ✅     |
| `t`   | Long   | `V0-api.md:139` (Point.t: number)   | ✅     |
| `p`   | Float? | `V0-api.md:140` (Point.p?: number)  | ✅     |
| `tx`  | Float? | `V0-api.md:141` (Point.tx?: number) | ✅     |
| `ty`  | Float? | `V0-api.md:142` (Point.ty?: number) | ✅     |
| `r`   | Float? | `V0-api.md:143` (Point.r?: number)  | ✅     |

**Field Naming Verification**:

- ✅ Uses `t` (not `timestamp`)
- ✅ Uses `p` (not `pressure`)
- ✅ Uses `tx`, `ty` (not `tiltX`, `tiltY`)
- ✅ Uses `r` (not `rotation` or `azimuth`)

**Units**:

- `t`: Unix milliseconds (Long) — matches API (number)
- `p`: 0..1 range (Float) — matches API
- `tx`, `ty`: radians (Float) — matches API
- `r`: rotation/azimuth (Float) — matches API

**Verdict**: ✅ Perfectly aligned. Field names match API contract exactly.

---

### 5. StrokeStyle Model

**File**: `apps/android/app/src/main/java/com/onyx/android/ink/model/Stroke.kt`

| Field            | Type      | V0 API Reference                                     | Status |
| ---------------- | --------- | ---------------------------------------------------- | ------ |
| `tool`           | Tool enum | `V0-api.md:128` (StrokeStyle.tool: "pen")            | ✅     |
| `color`          | String    | `V0-api.md:129` (StrokeStyle.color?: string)         | ✅     |
| `baseWidth`      | Float     | `V0-api.md:130` (StrokeStyle.baseWidth: number)      | ✅     |
| `minWidthFactor` | Float     | `V0-api.md:131` (StrokeStyle.minWidthFactor: number) | ✅     |
| `maxWidthFactor` | Float     | `V0-api.md:132` (StrokeStyle.maxWidthFactor: number) | ✅     |
| `nibRotation`    | Boolean   | `V0-api.md:133` (StrokeStyle.nibRotation: boolean)   | ✅     |

**Tool Enum Serialization Verification**:

```kotlin
@Serializable
enum class Tool(val apiValue: String) {
    @SerialName("pen") PEN("pen"),
    @SerialName("highlighter") HIGHLIGHTER("highlighter"),
    @SerialName("eraser") ERASER("eraser"),
}
```

- ✅ Serializes to lowercase strings: `"pen"`, `"highlighter"`, `"eraser"`
- ✅ Not using internal enum names (PEN → "pen", not "PEN")
- ✅ API contract specifies `tool: "pen"` for v0 (API line 128)
- ✅ Implementation supports "highlighter" and "eraser" for future compatibility

**Color Default**:

- Default: `"#000000"` (black) — reasonable fallback
- API marks color as optional (`color?: string`)

**Verdict**: ✅ Aligned. Tool serialization is sync-safe.

---

## RecognitionIndexEntity (Additional Table)

**File**: `apps/android/app/src/main/java/com/onyx/android/data/entity/RecognitionIndexEntity.kt`

This table is **not part of the core v0 API contract** but supports:

- Android-side MyScript recognition text extraction
- FTS4 full-text search (local search before sync)
- Future sync: recognition text can be uploaded as metadata or in op log

| Field                 | Purpose                 | Sync Impact                    |
| --------------------- | ----------------------- | ------------------------------ |
| `pageId`              | FK to PageEntity        | Safe (UUIDs align)             |
| `noteId`              | FK to NoteEntity        | Safe (UUIDs align)             |
| `recognizedText`      | MyScript output         | Can sync as PageMeta extension |
| `recognizedAtLamport` | Lamport timestamp       | Compatible with clock model    |
| `recognizerVersion`   | MyScript version string | Metadata only                  |
| `updatedAt`           | Unix ms                 | Matches API timestamp type     |

**Verdict**: ✅ Compatible. Can be synced as extended metadata without conflicts.

---

## Cross-Cutting Concerns

### ID Format Consistency

- ✅ All entity IDs use `String` type (Room entity PKs)
- ✅ Generated via `UUID.randomUUID().toString()` in repositories
- ✅ Matches API contract: `type NoteId = IdStr` (line 35)

### Timestamp Consistency

- ✅ All timestamps use `Long` (Unix milliseconds)
- ✅ Generated via `System.currentTimeMillis()`
- ✅ Matches API contract: `type UnixMs = number` (line 43)

### Lamport Clock

- ✅ `createdLamport: Long` field present on StrokeEntity
- ✅ `contentLamportMax: Long` field present on PageEntity
- ✅ Compatible with API op ordering: `(lamport, deviceId, opId)` (line 21)

### Geometry Units

- ✅ Pages use `unit = "pt"` (points) — matches API recommendation (line 50)
- ✅ PDF pages use MuPDF's native point units (1pt = 1/72 inch)
- ✅ Stroke coordinates in points relative to page origin

---

## Known Deviations (Intentional)

### 1. PageEntity.indexInNote

- **Type**: `Int`
- **Purpose**: Local ordering for multi-page notes (task 8.1)
- **Sync Impact**: None — this field is local-only, not transmitted
- **Justification**: Room doesn't guarantee insertion order; explicit index simplifies UI pagination

### 2. Preview Fields Omitted from NoteEntity

- **Missing**: `previewThumbAssetId`, `previewPageAssetId`, `previewUpdatedAt`
- **Reason**: Android v0 is authoring-only; preview generation happens server-side (Convex)
- **Sync Impact**: None — Android reads these from Convex queries, doesn't store locally

### 3. Tool Enum Extension

- **API Contract**: `tool: "pen"` (line 128) — v0 only supports pen
- **Implementation**: `Tool.PEN`, `Tool.HIGHLIGHTER`, `Tool.ERASER`
- **Justification**: Jetpack Ink API supports multiple tools; adding now for future-proofing
- **Sync Impact**: Safe — `@SerialName` ensures correct string serialization

---

## Migration Readiness

When sync is implemented (Milestone C), the following can be transmitted without schema changes:

### Notes

- All fields map 1:1 to `NoteMeta` type
- `ownerUserId` ready for multi-user scenarios

### Pages

- All fields map to `PageMeta` type
- `contentLamportMax` tracks latest op applied (ready for sync)
- `pdfAssetId` references Convex assets table

### Strokes

- Can serialize to `OpPayload.StrokeAdd` format directly:
  ```kotlin
  OpPayload.StrokeAdd(
      strokeId = stroke.id,
      createdLamport = stroke.createdLamport,
      points = stroke.points.map { Point(x, y, t, p, tx, ty, r) },
      style = stroke.style, // already JSON-serializable
      bounds = stroke.bounds // already {x, y, w, h}
  )
  ```

### Recognition Text

- `RecognitionIndexEntity.recognizedText` can sync as extended PageMeta or separate search index

---

## Conclusion

✅ **All Android entities are sync-compatible with the v0 API contract.**

No schema migrations required before implementing Convex sync (Milestone C). The offline-first architecture is ready for bidirectional synchronization.

**Verified By**: Schema audit task 8.4  
**Next Steps**: Implement Convex sync mutations (Milestone C)
