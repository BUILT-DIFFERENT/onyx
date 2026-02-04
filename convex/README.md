# Convex (system of record)

All canonical state lives in Convex: notes, pages, ops, commits, shares, public links, assets metadata, exports metadata, tiles/previews.

Planned structure:
- `schema.ts` canonical schema
- `functions/` queries, mutations, actions
- `jobs/` scheduled functions (snapshots, previews, exports, retention)
- `migrations/` backfills and schema migrations
- `fixtures/` seed data and contract fixtures
