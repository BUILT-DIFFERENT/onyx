import { z } from 'zod';
import { StrokeStyleSchema, BoundsSchema } from './common';

/**
 * StrokeSchema - JSON-serializable API contract for Stroke (METADATA ONLY)
 * See docs/schema-audit.md for canonical sync API contract
 *
 * Note: strokeData and points are intentionally excluded:
 * - strokeData is ByteArray (not JSON-serializable)
 * - points deferred to sync implementation (Milestone C)
 */
export const StrokeSchema = z
  .object({
    strokeId: z.string().uuid(),
    pageId: z.string().uuid(),
    style: StrokeStyleSchema, // Nested object
    bounds: BoundsSchema, // Nested object
    createdAt: z.number().int(), // Unix ms
    createdLamport: z.number().int(), // Lamport clock
  })
  .strict();

export type Stroke = z.infer<typeof StrokeSchema>;
