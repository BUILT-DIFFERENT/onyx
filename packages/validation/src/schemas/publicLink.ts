import { z } from 'zod';

/**
 * PublicLinkSchema — JSON-serializable API contract for Public Links
 * Aligned with convex/schema.ts `publicLinks` table and V0-api.md PublicLinkEntry.
 */
export const PublicLinkSchema = z
  .object({
    notebookId: z.string().uuid(),
    linkToken: z.string(), // high-entropy URL token
    createdByUserId: z.string(),
    createdAt: z.number().int(), // Unix ms
    revokedAt: z.number().int().optional(),
  })
  .strict();

export type PublicLink = z.infer<typeof PublicLinkSchema>;
