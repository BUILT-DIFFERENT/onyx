# Search Text Contracts (Handwriting + PDF)

Date: 2026-03-07 (updated)

## Scope

Defines the cross-surface contract for search text pushed by Android to Convex.

## Architecture

- Android pushes recognized handwriting text (MyScript IInk) and PDF-extracted text (PDFBox) to Convex via `search.upsertPageText`.
- Convex stores this in the `searchTexts` table.
- Web/global search uses Convex full-text search indexes (`search_content` on `recognizedText`, `search_pdf_content` on `pdfText`).

## Canonical Sources

- Convex table: `searchTexts` in `convex/schema.ts`
- Validation schema: `packages/validation/src/schemas/searchText.ts`
- Fixture: `tests/contracts/fixtures/search-text.fixture.json`
- API endpoint: `search.upsertPageText`, `search.global` in `V0-api.md` §3.10

## Contract Shape

| Field | Type | Notes |
|---|---|---|
| notebookId | string | FK → notebooks |
| pageId | string | FK → pages |
| recognizedText | string? | IInk HWR output |
| pdfText | string? | PDFBox extracted text |
| source | enum | `"handwriting"` \| `"pdfText"` \| `"both"` |
| extractedAt | number | Unix ms |

## Notes

- Search deduplication is handled by upsert semantics on (notebookId, pageId) — not by token-level `mergeKey`. The old token-level model was removed.
- Both `recognizedText` and `pdfText` are indexed for full-text search via separate Convex search indexes.
- `search.global` queries both indexes and merges results by relevance + recency.
