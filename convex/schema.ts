import { defineSchema, defineTable } from 'convex/server';
import { v } from 'convex/values';

export default defineSchema({
  notes: defineTable({
    noteId: v.string(), // UUID string
    ownerUserId: v.string(), // Clerk user ID
    title: v.string(),
    createdAt: v.number(), // Unix ms timestamp
    updatedAt: v.number(), // Unix ms timestamp
    deletedAt: v.optional(v.number()), // Unix ms timestamp, absent = not deleted
    // Note: folderId is not included because it's local-only metadata.
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_noteId', ['noteId']),
  pageObjects: defineTable({
    objectId: v.string(),
    pageId: v.string(),
    noteId: v.string(),
    kind: v.union(v.literal('shape'), v.literal('image'), v.literal('text')),
    zIndex: v.number(),
    x: v.number(),
    y: v.number(),
    width: v.number(),
    height: v.number(),
    rotationDeg: v.number(),
    payload: v.union(
      v.object({
        shapeType: v.union(v.literal('line'), v.literal('rectangle'), v.literal('ellipse')),
        strokeColor: v.optional(v.string()),
        strokeWidth: v.optional(v.number()),
        fillColor: v.optional(v.union(v.string(), v.null())),
      }),
      v.object({
        assetId: v.optional(v.union(v.string(), v.null())),
        mimeType: v.optional(v.union(v.string(), v.null())),
      }),
      v.object({
        text: v.optional(v.string()),
        align: v.optional(v.union(v.literal('start'), v.literal('center'), v.literal('end'))),
      }),
    ),
    createdAt: v.number(),
    updatedAt: v.number(),
    deletedAt: v.optional(v.number()),
  })
    .index('by_object_id', ['objectId'])
    .index('by_page', ['pageId'])
    .index('by_note', ['noteId'])
    .index('by_kind', ['kind'])
    .index('by_page_zindex', ['pageId', 'zIndex']),
});
