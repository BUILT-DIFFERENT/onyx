import { z } from 'zod';

/**
 * BoundsSchema — reusable bounding box schema
 * Used by search hit positions and other spatial references.
 */
export const BoundsSchema = z.object({
  x: z.number(),
  y: z.number(),
  w: z.number(),
  h: z.number(),
});

export type Bounds = z.infer<typeof BoundsSchema>;
