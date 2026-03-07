import { z } from 'zod';

/**
 * SearchTextSchema — JSON-serializable API contract for Search Texts
 * Replaces the old SearchIndexTokenSchema. Aligned with convex/schema.ts `searchTexts` table
 * and V0-api.md search.upsertPageText.
 *
 * HWR recognized text and PDF extracted text, pushed by Android to Convex for global search.
 */
export const SearchTextSchema = z
  .object({
    notebookId: z.string().uuid(),
    pageId: z.string().uuid(),
    recognizedText: z.string().optional(), // IInk HWR output
    pdfText: z.string().optional(), // PDFBox extracted text
    source: z.enum(['handwriting', 'pdfText', 'both']),
    extractedAt: z.number().int(), // Unix ms
  })
  .strict();

export type SearchText = z.infer<typeof SearchTextSchema>;
