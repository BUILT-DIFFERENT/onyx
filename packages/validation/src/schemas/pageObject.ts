import { z } from 'zod';

const PageObjectBaseSchema = z
  .object({
    objectId: z.string().uuid(),
    pageId: z.string().uuid(),
    noteId: z.string().uuid(),
    zIndex: z.number().int(),
    x: z.number(),
    y: z.number(),
    width: z.number(),
    height: z.number(),
    rotationDeg: z.number(),
    createdAt: z.number().int(),
    updatedAt: z.number().int(),
    deletedAt: z.number().int().nullable().optional(),
  })
  .strict();

export const ShapePayloadSchema = z
  .object({
    shapeType: z.enum(['line', 'rectangle', 'ellipse']),
    strokeColor: z.string().optional(),
    strokeWidth: z.number().optional(),
    fillColor: z.string().nullable().optional(),
  })
  .strict();

export const ImagePayloadSchema = z
  .object({
    assetId: z.string().nullable().optional(),
    mimeType: z.string().nullable().optional(),
  })
  .strict();

export const TextPayloadSchema = z
  .object({
    text: z.string().optional(),
    align: z.enum(['start', 'center', 'end']).optional(),
  })
  .strict();

export const PageObjectSchema = z.discriminatedUnion('kind', [
  PageObjectBaseSchema.extend({
    kind: z.literal('shape'),
    payload: ShapePayloadSchema,
  }),
  PageObjectBaseSchema.extend({
    kind: z.literal('image'),
    payload: ImagePayloadSchema,
  }),
  PageObjectBaseSchema.extend({
    kind: z.literal('text'),
    payload: TextPayloadSchema,
  }),
]);

export type PageObject = z.infer<typeof PageObjectSchema>;
export type ShapePayload = z.infer<typeof ShapePayloadSchema>;
