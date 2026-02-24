import { mutation, query } from '../_generated/server';
import { v } from 'convex/values';

export const list = query({
  args: {},
  handler: async (ctx) => {
    return await ctx.db.query('notes').collect();
  },
});

export const listPageObjectsByPage = query({
  args: {
    pageId: v.string(),
  },
  handler: async (ctx, args) => {
    return await ctx.db
      .query('pageObjects')
      .withIndex('by_page', (q) => q.eq('pageId', args.pageId))
      .collect();
  },
});

export const upsertPageObjectWithConflictMetadata = mutation({
  args: {
    objectId: v.string(),
    pageId: v.string(),
    noteId: v.string(),
    kind: v.union(
      v.literal('shape'),
      v.literal('image'),
      v.literal('text'),
      v.literal('audio'),
      v.literal('sticky'),
      v.literal('scan'),
      v.literal('file'),
    ),
    zIndex: v.number(),
    x: v.number(),
    y: v.number(),
    width: v.number(),
    height: v.number(),
    rotationDeg: v.number(),
    payload: v.any(),
    deletedAt: v.optional(v.number()),
    sync: v.optional(
      v.object({
        objectRevision: v.optional(v.number()),
        parentRevision: v.optional(v.number()),
        lastMutationId: v.optional(v.string()),
        conflictPolicy: v.optional(v.union(v.literal('lastWriteWins'), v.literal('manualResolve'))),
      }),
    ),
  },
  handler: async (ctx, args) => {
    const now = Date.now();
    const existing = await ctx.db.query('pageObjects').withIndex('by_object_id', (q) => q.eq('objectId', args.objectId)).unique();

    const existingRevision = existing?.sync?.objectRevision ?? 0;
    const objectRevision = Math.max(existingRevision + 1, args.sync?.objectRevision ?? 0);
    const parentRevision = args.sync?.parentRevision ?? (existingRevision > 0 ? existingRevision : undefined);
    const conflictPolicy = args.sync?.conflictPolicy ?? existing?.sync?.conflictPolicy ?? 'lastWriteWins';
    const lastMutationId = args.sync?.lastMutationId ?? `${args.objectId}:${now}`;

    const document = {
      objectId: args.objectId,
      pageId: args.pageId,
      noteId: args.noteId,
      kind: args.kind,
      zIndex: args.zIndex,
      x: args.x,
      y: args.y,
      width: args.width,
      height: args.height,
      rotationDeg: args.rotationDeg,
      payload: args.payload,
      sync: {
        objectRevision,
        parentRevision,
        lastMutationId,
        conflictPolicy,
      },
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      deletedAt: args.deletedAt,
    };

    if (existing?._id) {
      await ctx.db.patch(existing._id, document);
      return { objectId: args.objectId, objectRevision, conflictPolicy, updated: true };
    }

    await ctx.db.insert('pageObjects', document);
    return { objectId: args.objectId, objectRevision, conflictPolicy, updated: false };
  },
});
