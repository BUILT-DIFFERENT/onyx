# Data Model (canonical)

Convex tables (v0 intent):
- `users` (Clerk identity + tokenIdentifier)
- `notes`, `pages`
- `ops` (per-page op log)
- `commits` (snapshots)
- `shares`, `publicLinks`
- `assets` (metadata for blobs)
- `exports` (metadata)
- `tiles` / `previews`

Derived/read-optimized data lives in Convex tables or computed views. No external KV/cache in v0.
