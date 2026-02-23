import { z } from 'zod';

/**
 * PageSchema - JSON-serializable API contract for Page
 * Canonical sync contract lives in packages/validation + tests/contracts fixtures.
 *
 * Note: indexInNote is intentionally excluded - it's a local-only field.
 */
export const PageSchema = z
  .object({
    pageId: z.string().uuid(),
    noteId: z.string().uuid(),
    kind: z.enum(['ink', 'pdf', 'mixed', 'infinite']),
    geometryKind: z.enum(['fixed', 'infinite']),
    width: z.number(), // In points
    height: z.number(), // In points
    unit: z.literal('pt'),
    pdfAssetId: z.string().nullable().optional(), // For PDF pages
    pdfPageNo: z.number().int().nullable().optional(), // PDF page number
    contentLamportMax: z.number().int(), // Sync tracking
    updatedAt: z.number().int(), // Unix ms
  })
  .strict();

export type Page = z.infer<typeof PageSchema>;
