import { z } from 'zod';

/**
 * PageSchema — JSON-serializable API contract for Page
 * Aligned with convex/schema.ts `pages` table and V0-api.md PageMeta.
 *
 * All dimensions in pixels (px). Default page: 794 × 1123 px (A4 at 96 DPI).
 * Infinite canvas pages have heightPx = 0.
 */
export const PageSchema = z
  .object({
    pageId: z.string().uuid(),
    notebookId: z.string().uuid(), // FK → notebooks
    order: z.number(), // sort order within notebook
    templateType: z.enum(['blank', 'lined', 'dotted', 'grid']),
    templateDensity: z.number(),
    templateLineWidth: z.number(),
    backgroundColorHex: z.string(),
    widthPx: z.number(), // default 794; A4 at 96 DPI
    heightPx: z.number(), // default 1123; 0 = infinite canvas page
    pdfAssetId: z.string().optional(), // FK → assets.assetId
    pdfPageNo: z.number().int().optional(), // 0-based index into PDF

    // Per-page Yjs snapshot (R2)
    pageSnapshotUrl: z.string().optional(),
    pageSnapshotStateVector: z.string().optional(), // base64-encoded

    updatedAt: z.number().int(), // Unix ms
  })
  .strict();

export type Page = z.infer<typeof PageSchema>;
