import { z } from 'zod';

/**
 * StrokeStyleSchema - matches V0-api.md:127-134
 * Style properties for a stroke (pen, highlighter, eraser)
 */
export const StrokeStyleSchema = z.object({
  tool: z.enum(['pen', 'highlighter', 'eraser']),
  color: z.string().optional(), // hex color
  baseWidth: z.number(),
  minWidthFactor: z.number(),
  maxWidthFactor: z.number(),
  nibRotation: z.boolean(),
});

export type StrokeStyle = z.infer<typeof StrokeStyleSchema>;

/**
 * BoundsSchema - matches V0-api.md:107-110
 * Bounding box for a stroke
 */
export const BoundsSchema = z.object({
  x: z.number(),
  y: z.number(),
  w: z.number(),
  h: z.number(),
});

export type Bounds = z.infer<typeof BoundsSchema>;
