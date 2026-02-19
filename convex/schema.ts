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
    // Note: folderId is NOT included - it's local-only per docs/schema-audit.md
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_noteId', ['noteId']),
});
