# System Overview (v0)

Android (authoring) ── Convex (system of record + realtime + jobs) ── Web (view-only)
│
└── Object Storage (raw blobs only)

v0 is view-only on web and does not use Cloudflare.

## Clients
- Android app: Kotlin + Room + Convex Android client. Offline-first with op queue and deterministic replay.
- Web app: TanStack Start + shadcn/ui + zod. View-only for v0, subscriptions for live updates.

## System of Record
Convex stores all user-visible canonical state (notes, pages, ops, commits, shares, public links, assets metadata, exports metadata, tiles/previews).

## Storage
S3-compatible object storage for raw blobs. Convex issues presigned URLs for PUT/GET and tracks metadata.

## Background Jobs
Convex scheduled functions for snapshots, preview generation, exports, and retention cleanup.
