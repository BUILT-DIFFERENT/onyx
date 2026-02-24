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
    sourceUri: z.string().nullable().optional(),
    displayName: z.string().nullable().optional(),
  })
  .strict();

export const TextPayloadSchema = z
  .object({
    text: z.string().optional().default('Text'),
    align: z.enum(['start', 'center', 'end']).optional(),
    color: z.string().optional(),
    fontSizeSp: z.number().optional(),
    bold: z.boolean().optional(),
    italic: z.boolean().optional(),
    underline: z.boolean().optional(),
  })
  .strict();

export const AudioPayloadSchema = z
  .object({
    assetId: z.string().nullable().optional(),
    mimeType: z.string().nullable().optional(),
    durationMs: z.number().int().nonnegative().optional(),
    waveformAssetId: z.string().nullable().optional(),
  })
  .strict();

export const StickyPayloadSchema = z
  .object({
    text: z.string().optional(),
    color: z.string().optional(),
    style: z.enum(['square', 'rounded']).optional(),
  })
  .strict();

export const ScanPayloadSchema = z
  .object({
    assetId: z.string().nullable().optional(),
    pageCount: z.number().int().positive().optional(),
    source: z.enum(['camera', 'gallery']).optional(),
  })
  .strict();

export const FilePayloadSchema = z
  .object({
    assetId: z.string().nullable().optional(),
    fileName: z.string().optional(),
    mimeType: z.string().nullable().optional(),
    sizeBytes: z.number().int().nonnegative().optional(),
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
  PageObjectBaseSchema.extend({
    kind: z.literal('audio'),
    payload: AudioPayloadSchema,
  }),
  PageObjectBaseSchema.extend({
    kind: z.literal('sticky'),
    payload: StickyPayloadSchema,
  }),
  PageObjectBaseSchema.extend({
    kind: z.literal('scan'),
    payload: ScanPayloadSchema,
  }),
  PageObjectBaseSchema.extend({
    kind: z.literal('file'),
    payload: FilePayloadSchema,
  }),
]);

export type PageObject = z.infer<typeof PageObjectSchema>;
export type ShapePayload = z.infer<typeof ShapePayloadSchema>;
export type ImagePayload = z.infer<typeof ImagePayloadSchema>;
export type TextPayload = z.infer<typeof TextPayloadSchema>;
export type AudioPayload = z.infer<typeof AudioPayloadSchema>;
export type StickyPayload = z.infer<typeof StickyPayloadSchema>;
export type ScanPayload = z.infer<typeof ScanPayloadSchema>;
export type FilePayload = z.infer<typeof FilePayloadSchema>;
