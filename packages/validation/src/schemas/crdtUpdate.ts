import { z } from 'zod';

/**
 * CrdtUpdateSchema — JSON-serializable API contract for CRDT Update deltas
 * Aligned with convex/schema.ts `crdtUpdates` table and V0-api.md CrdtUpdate type.
 *
 * Small Yjs update payloads (<10 KB each), stored temporarily in Convex.
 * Cleaned up after snapshots are written to R2.
 */
export const CrdtUpdateSchema = z
  .object({
    notebookId: z.string().uuid(),
    pageId: z.string().uuid().optional(), // null = notebook-level doc
    authorUserId: z.string(),
    deviceId: z.string(), // UUID from client
    updateBinary: z.string(), // base64-encoded Yjs update binary
    createdAt: z.number().int(), // Unix ms
  })
  .strict();

export type CrdtUpdate = z.infer<typeof CrdtUpdateSchema>;
