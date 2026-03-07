import { z } from 'zod';

/**
 * ExportSchema — JSON-serializable API contract for Exports (user-initiated PDF export)
 * Aligned with convex/schema.ts `exports` table and V0-api.md exports.register.
 *
 * Replaces the old ExportMetadataSchema which had status tracking and layered mode.
 * V0 supports flattened export only; no queued/running/failed status tracking.
 */
export const ExportSchema = z
  .object({
    notebookId: z.string().uuid(),
    exportAssetId: z.string().uuid(), // FK → assets.assetId
    mode: z.literal('flattened'), // v0: flattened only
    createdByUserId: z.string(),
    createdAt: z.number().int(), // Unix ms
  })
  .strict();

export type Export = z.infer<typeof ExportSchema>;
