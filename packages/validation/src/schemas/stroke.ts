import { z } from 'zod';

/**
 * StrokeMetaSchema — JSON-serializable API contract for Stroke metadata
 * Aligned with PLAN.md §5.1 Stroke entity.
 *
 * Strokes live inside per-page Yjs docs. This schema is for the metadata-only contract
 * used by search/hit-testing cache (Room on Android, contract tests). It does NOT contain
 * raw point data (which is inside the Yjs binary).
 *
 * The old StrokeSchema used StrokeStyleSchema + BoundsSchema + createdLamport.
 * Those have been replaced with inline fields matching the CRDT data model.
 */
export const StrokeMetaSchema = z
  .object({
    strokeId: z.string().uuid(),
    pageId: z.string().uuid(),
    toolType: z.enum(['pen', 'highlighter']),
    penType: z.enum(['ballpoint', 'fountain', 'pencil']).optional(), // only for toolType=pen
    colorHex: z.string(),
    thickness: z.number(), // base width in px
    opacity: z.number(), // 0.0–1.0 (mainly for highlighter)
    pressureSensitivity: z.number(), // 0.0–1.0
    tiltSensitivity: z.number(), // 0.0–1.0
    stabilization: z.number(), // 0.0–1.0 smoothing level
    isShape: z.boolean(), // true if IInk shape-recognized and snapped
    createdAt: z.number().int(), // Unix ms
  })
  .strict();

export type StrokeMeta = z.infer<typeof StrokeMetaSchema>;
