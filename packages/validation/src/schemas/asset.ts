import { z } from 'zod';

/**
 * AssetSchema — JSON-serializable API contract for Assets (R2 blob references)
 * Aligned with convex/schema.ts `assets` table and V0-api.md AssetMeta.
 *
 * Convex stores metadata only; actual binary lives in Cloudflare R2.
 */
export const AssetSchema = z
  .object({
    assetId: z.string().uuid(),
    ownerUserId: z.string(),
    notebookId: z.string().uuid().optional(),
    type: z.enum(['pdf', 'image', 'audio', 'snapshot', 'exportPdf', 'thumbnail']),
    r2Key: z.string(),
    contentType: z.string(),
    size: z.number().int().optional(),
    sha256: z.string().optional(),
    confirmed: z.boolean(), // false until client confirms upload completed
    createdAt: z.number().int(), // Unix ms
  })
  .strict();

export type Asset = z.infer<typeof AssetSchema>;
