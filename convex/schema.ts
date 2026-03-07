import { defineSchema, defineTable } from 'convex/server';
import { v } from 'convex/values';

export default defineSchema({
  // ─── Users ───────────────────────────────────────────────────────────
  users: defineTable({
    clerkUserId: v.string(),
    email: v.string(),
    displayName: v.string(),
    avatarUrl: v.optional(v.string()),
    createdAt: v.number(),
    updatedAt: v.number(),
  })
    .index('by_clerk_user_id', ['clerkUserId'])
    .index('by_email', ['email']),

  // ─── Folders (server-synced, arbitrary nesting) ──────────────────────
  folders: defineTable({
    folderId: v.string(), // UUID
    ownerUserId: v.string(), // FK → users.clerkUserId
    parentFolderId: v.optional(v.string()), // self-ref; null = root
    name: v.string(),
    createdAt: v.number(),
    updatedAt: v.number(),
  })
    .index('by_folder_id', ['folderId'])
    .index('by_owner', ['ownerUserId']),

  // ─── Notebooks ───────────────────────────────────────────────────────
  notebooks: defineTable({
    notebookId: v.string(), // UUID
    ownerUserId: v.string(), // FK → users.clerkUserId
    folderId: v.optional(v.string()), // FK → folders.folderId; null = root
    title: v.string(),
    coverColor: v.string(), // hex
    isFavorite: v.boolean(),
    notebookMode: v.union(v.literal('paged'), v.literal('infinite_canvas')),
    createdAt: v.number(),
    updatedAt: v.number(),
    deletedAt: v.optional(v.number()), // soft delete; null = active

    // Notebook-level Yjs snapshot (R2)
    snapshotUrl: v.optional(v.string()),
    snapshotStateVector: v.optional(v.string()), // base64-encoded

    // First-page preview (for library grid)
    previewThumbAssetId: v.optional(v.string()),
    previewPageAssetId: v.optional(v.string()),
    previewUpdatedAt: v.optional(v.number()),

    // Sharing metadata (denormalized for list queries)
    hasPublicLink: v.optional(v.boolean()),
    shareCount: v.optional(v.number()),
  })
    .index('by_notebook_id', ['notebookId'])
    .index('by_owner', ['ownerUserId'])
    .index('by_folder', ['folderId'])
    .index('by_owner_favorite', ['ownerUserId', 'isFavorite']),

  // ─── Pages ───────────────────────────────────────────────────────────
  pages: defineTable({
    pageId: v.string(), // UUID
    notebookId: v.string(), // FK → notebooks.notebookId
    order: v.number(), // sort order within notebook
    templateType: v.union(
      v.literal('blank'),
      v.literal('lined'),
      v.literal('dotted'),
      v.literal('grid'),
    ),
    templateDensity: v.number(),
    templateLineWidth: v.number(),
    backgroundColorHex: v.string(),
    widthPx: v.number(), // default 794; A4 at 96 DPI
    heightPx: v.number(), // default 1123; 0 = infinite canvas page
    pdfAssetId: v.optional(v.string()), // FK → assets.assetId
    pdfPageNo: v.optional(v.number()), // 0-based index into PDF

    // Per-page Yjs snapshot (R2)
    pageSnapshotUrl: v.optional(v.string()),
    pageSnapshotStateVector: v.optional(v.string()), // base64-encoded

    updatedAt: v.number(),
  })
    .index('by_page_id', ['pageId'])
    .index('by_notebook', ['notebookId'])
    .index('by_notebook_order', ['notebookId', 'order']),

  // ─── CRDT Updates (Yjs binary deltas) ────────────────────────────────
  // Small Yjs update payloads (<10 KB each), stored temporarily in Convex.
  // Cleaned up after snapshots are written to R2.
  crdtUpdates: defineTable({
    notebookId: v.string(),
    pageId: v.optional(v.string()), // null = notebook-level doc
    authorUserId: v.string(),
    deviceId: v.string(),
    updateBinary: v.string(), // base64-encoded Yjs update binary
    createdAt: v.number(),
  })
    .index('by_notebook_page', ['notebookId', 'pageId', 'createdAt'])
    .index('by_notebook', ['notebookId', 'createdAt']),

  // ─── Shares (invite-by-email) ────────────────────────────────────────
  shares: defineTable({
    notebookId: v.string(),
    granteeUserId: v.string(),
    role: v.union(v.literal('viewer'), v.literal('editor')),
    grantedByUserId: v.string(),
    createdAt: v.number(),
    revokedAt: v.optional(v.number()),
  })
    .index('by_notebook', ['notebookId'])
    .index('by_grantee', ['granteeUserId']),

  // ─── Public Links ────────────────────────────────────────────────────
  publicLinks: defineTable({
    notebookId: v.string(),
    linkToken: v.string(), // high-entropy URL token
    createdByUserId: v.string(),
    createdAt: v.number(),
    revokedAt: v.optional(v.number()),
  })
    .index('by_link_token', ['linkToken'])
    .index('by_notebook', ['notebookId']),

  // ─── Assets (R2 blob references) ─────────────────────────────────────
  // Convex stores metadata only; actual binary lives in Cloudflare R2.
  assets: defineTable({
    assetId: v.string(), // UUID
    ownerUserId: v.string(),
    notebookId: v.optional(v.string()),
    type: v.union(
      v.literal('pdf'),
      v.literal('image'),
      v.literal('audio'),
      v.literal('snapshot'),
      v.literal('exportPdf'),
      v.literal('thumbnail'),
    ),
    r2Key: v.string(),
    contentType: v.string(),
    size: v.optional(v.number()),
    sha256: v.optional(v.string()),
    confirmed: v.boolean(), // false until client confirms upload completed
    createdAt: v.number(),
  })
    .index('by_asset_id', ['assetId'])
    .index('by_owner', ['ownerUserId'])
    .index('by_notebook', ['notebookId']),

  // ─── Presence (collaborator cursors) ─────────────────────────────────
  // Updated every 500ms while drawing; auto-expires after 10s of no updates.
  presence: defineTable({
    notebookId: v.string(),
    pageId: v.string(),
    userId: v.string(),
    cursorX: v.number(), // px, page-space
    cursorY: v.number(), // px, page-space
    activeTool: v.string(), // "pen" | "highlighter" | "eraser" | "lasso"
    activeColor: v.optional(v.string()), // hex
    updatedAt: v.number(),
  })
    .index('by_notebook', ['notebookId'])
    .index('by_user_notebook', ['userId', 'notebookId']),

  // ─── Search Texts ────────────────────────────────────────────────────
  // HWR recognized text and PDF extracted text, pushed by Android.
  searchTexts: defineTable({
    notebookId: v.string(),
    pageId: v.string(),
    recognizedText: v.optional(v.string()), // IInk HWR output
    pdfText: v.optional(v.string()), // PDFBox extracted text
    source: v.union(
      v.literal('handwriting'),
      v.literal('pdfText'),
      v.literal('both'),
    ),
    extractedAt: v.number(),
  })
    .index('by_notebook', ['notebookId'])
    .index('by_page', ['pageId'])
    .searchIndex('search_content', {
      searchField: 'recognizedText',
      filterFields: ['notebookId'],
    })
    .searchIndex('search_pdf_content', {
      searchField: 'pdfText',
      filterFields: ['notebookId'],
    }),

  // ─── Exports (user-initiated PDF export) ─────────────────────────────
  exports: defineTable({
    notebookId: v.string(),
    exportAssetId: v.string(), // FK → assets.assetId
    mode: v.literal('flattened'), // v0: flattened only
    createdByUserId: v.string(),
    createdAt: v.number(),
  })
    .index('by_notebook', ['notebookId']),
});
