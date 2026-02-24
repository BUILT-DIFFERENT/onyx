# Search Index Contracts (Handwriting + PDF OCR)

Date: 2026-02-24

## Scope

This document defines the cross-surface contract for persisted search tokens generated from:

- Handwriting recognition (`source = handwriting`)
- PDF OCR extraction (`source = pdfOcr`)

Runtime indexing/sync is intentionally out of scope for this wave. This is schema + fixture groundwork.

## Canonical Contract Sources

- Validation schema: `C:/onyx/packages/validation/src/schemas/searchIndexToken.ts`
- Convex table: `C:/onyx/convex/schema.ts` (`searchIndexTokens`)
- Fixtures: `C:/onyx/tests/contracts/fixtures/search-index-*.fixture.json`

## Contract Shape

Shared required fields:

- `tokenId`, `noteId`, `pageId`
- `token`, `displayText?`
- `source`: `handwriting | pdfOcr`
- `bounds`: `{ x, y, width, height, rotationDeg? }`
- `indexVersion`: positive integer schema version
- `mergeKey`: deterministic dedupe key
- `sourceRevision`: non-negative integer revision from upstream source
- `sourceUpdatedAt`, `indexedAt`
- `payload` (source-specific)
- `deletedAt?`

Source-specific payloads:

- `handwriting` payload:
  - `recognitionProvider?`: `myscript | mlkit | other`
  - `confidence?`: `0..1`
  - `strokeIds?`: UUID list
- `pdfOcr` payload:
  - `pdfAssetId`
  - `pdfPageNo` (non-negative)
  - `ocrEngine?`: `pdfium | mlkit | tesseract | other`
  - `confidence?`: `0..1`

## Merge / Conflict Policy (Contract-Level)

Use `mergeKey` as the logical identity for dedupe and merge.

When two entries have the same `mergeKey`:

1. Higher `sourceRevision` wins.
2. If `sourceRevision` ties, higher `sourceUpdatedAt` wins.
3. If still tied, higher `indexedAt` wins.

Deletion semantics:

- Tombstones are represented via `deletedAt`.
- A non-null `deletedAt` record wins over older non-deleted records for the same `mergeKey`.

## Web Fallback Expectations

Web clients that do not yet consume this table should ignore unknown token metadata without failing note/page decode.
Search UI must not assume availability of this index until runtime sync/query work lands.
