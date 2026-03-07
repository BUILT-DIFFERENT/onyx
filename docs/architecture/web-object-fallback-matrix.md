# Web Object Fallback Matrix

Date: 2026-03-07 (updated)
Scope: Decode and fallback expectations for Android-authored page objects before full web rendering lands.

## Purpose

Web acts as a view-only surface. This matrix defines deterministic behavior when notebooks include object types that the web viewer does not yet render.

## Decode Policy

1. Web clients receive page content via Yjs replay engine (Yjs snapshot + Convex deltas).
2. Objects are parsed from the per-page Yjs doc `objects: Y.Map`.
3. Unknown object `type` values must be preserved in the local model and ignored by rendering.
4. Unknown fields on known `type` values must be ignored (forward-compatible decode).
5. Objects that fail validation should be dropped individually, not crash the page render.

## Rendering / UX Fallback

| Type | Web runtime behavior (Milestone B) | User-visible affordance |
|---|---|---|
| `textBlock` | Render text; KaTeX for `isLatex=true` | Full render |
| `image` | Render from R2 asset | Full render |
| `stickyNote` | Render colored card with text | Full render |
| `table` | Render grid with text cells | Full render |
| `audioAttachment` | Metadata only (no playback UI) | "Audio — open in app" hint |
| `shape` | Render geometric primitive on Canvas | Full render |
| Unknown future type | Preserve in model, skip rendering | No hard error |

## Error Handling

1. Validation failures logged with object ID and type.
2. Fallback must not block notebook open.
3. Partial decode success returns all successfully parsed objects.

## Non-Object Metadata Fallback

| Contract | Decode behavior | Notes |
|---|---|---|
| Template fields | Per-page Yjs doc metadata | Consumed for background rendering |
| Search text | Convex `searchTexts` table | Not visible in web UI directly |
| Export records | Convex `exports` table | Optional download badge |

## Test Expectations

1. Contract tests validate all 7 object type fixtures.
2. Contract tests include negative cases for type mismatch.
3. Web decode tests assert "skip invalid object, keep page alive" semantics.
4. Web decode tests assert unknown types are preserved in raw model.
