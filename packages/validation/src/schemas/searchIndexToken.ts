import { z } from 'zod';

const TokenBoundsSchema = z
  .object({
    x: z.number(),
    y: z.number(),
    width: z.number().positive(),
    height: z.number().positive(),
    rotationDeg: z.number().optional(),
  })
  .strict();

const SearchIndexTokenBaseSchema = z
  .object({
    tokenId: z.string().uuid(),
    noteId: z.string().uuid(),
    pageId: z.string().uuid(),
    token: z.string().min(1),
    displayText: z.string().optional(),
    bounds: TokenBoundsSchema,
    indexVersion: z.number().int().positive(),
    mergeKey: z.string().min(1),
    sourceRevision: z.number().int().nonnegative(),
    sourceUpdatedAt: z.number().int(),
    indexedAt: z.number().int(),
    deletedAt: z.number().int().nullable().optional(),
  })
  .strict();

const HandwritingPayloadSchema = z
  .object({
    recognitionProvider: z.enum(['myscript', 'mlkit', 'other']).optional(),
    confidence: z.number().min(0).max(1).optional(),
    strokeIds: z.array(z.string().uuid()).optional(),
  })
  .strict();

const PdfOcrPayloadSchema = z
  .object({
    pdfAssetId: z.string(),
    pdfPageNo: z.number().int().nonnegative(),
    ocrEngine: z.enum(['pdfium', 'mlkit', 'tesseract', 'other']).optional(),
    confidence: z.number().min(0).max(1).optional(),
  })
  .strict();

export const SearchIndexTokenSchema = z.discriminatedUnion('source', [
  SearchIndexTokenBaseSchema.extend({
    source: z.literal('handwriting'),
    payload: HandwritingPayloadSchema,
  }),
  SearchIndexTokenBaseSchema.extend({
    source: z.literal('pdfOcr'),
    payload: PdfOcrPayloadSchema,
  }),
]);

export type SearchIndexToken = z.infer<typeof SearchIndexTokenSchema>;
export type TokenBounds = z.infer<typeof TokenBoundsSchema>;
