import { z } from 'zod';

/**
 * ShareSchema — JSON-serializable API contract for Share (invite-by-email)
 * Aligned with convex/schema.ts `shares` table and V0-api.md ShareEntry.
 */
export const ShareSchema = z
  .object({
    notebookId: z.string().uuid(),
    granteeUserId: z.string(),
    role: z.enum(['viewer', 'editor']),
    grantedByUserId: z.string(),
    createdAt: z.number().int(), // Unix ms
    revokedAt: z.number().int().optional(),
  })
  .strict();

export type Share = z.infer<typeof ShareSchema>;
