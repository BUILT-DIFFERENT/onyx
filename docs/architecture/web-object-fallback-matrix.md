# Web Object Fallback Matrix

Date: 2026-02-23
Scope: Decode and fallback expectations for Android-authored `pageObjects` contracts before full web runtime rendering lands.

## Purpose

Web currently acts as a view-only surface for note metadata. This matrix defines deterministic behavior when notes include object metadata introduced by Android object insert flows.

## Decode Policy

1. Web clients must parse `pageObjects` as a discriminated union by `kind`.
2. Unknown `kind` values must be preserved in raw metadata and ignored by rendering.
3. Unknown payload fields on known `kind` values must be ignored (forward-compatible decode).
4. Known required fields that fail validation should drop only that object, not the entire note payload.

## Rendering / UX Fallback

| Kind | Contract status | Web runtime behavior (current wave) | User-visible affordance |
| --- | --- | --- | --- |
| `shape` | Supported contract | Decode and keep in local model; no canvas rendering yet | Optional non-blocking “Object unsupported on web yet” hint at note level |
| `image` | Supported contract | Decode and keep metadata only (no render) | Same note-level unsupported hint |
| `text` | Supported contract | Decode and keep metadata only (no render) | Same note-level unsupported hint |
| `audio` | Supported contract | Decode and keep metadata only (no playback UI) | Same note-level unsupported hint |
| `sticky` | Supported contract | Decode and keep metadata only (no render) | Same note-level unsupported hint |
| `scan` | Supported contract | Decode and keep metadata only (no render) | Same note-level unsupported hint |
| `file` | Supported contract | Decode and keep metadata only (no render/open) | Same note-level unsupported hint |
| Unknown future kind | Forward-compatible | Preserve raw metadata, skip rendering | No hard error; optional debug telemetry |

## Error Handling

1. Validation failures should be logged with object ID and kind where available.
2. Rendering fallback must not block note open.
3. Partial object decode success should still return successfully parsed objects.

## Non-Object Metadata Fallback (Current Wave Expansion)

Web decode should also tolerate new metadata contracts that are not yet rendered in web runtime:

| Contract | Decode behavior | Runtime behavior | User-visible affordance |
| --- | --- | --- | --- |
| `gestureSettings` | Parse if available; ignore unknown enum values by dropping invalid profile only | Not consumed in web editor (view-only) | None |
| `templateScopes` | Parse and keep metadata for future template fidelity | No direct rendering changes in this wave | None |
| `exportMetadata` | Parse and keep status/mode metadata | No export management UI in this wave | Optional informational badge in future |
| `searchIndexTokens` | Parse and preserve where available | Search UI not wired yet; do not fail note open | None |

## Test Expectations

1. Contract tests validate `shape`, `image`, `text`, `audio`, `sticky`, `scan`, and `file` fixtures.
2. Contract tests include negative cases for payload-kind mismatch.
3. Web decode tests should assert “skip invalid object, keep note payload alive” semantics.
