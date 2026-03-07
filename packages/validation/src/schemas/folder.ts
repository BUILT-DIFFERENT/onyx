import { z } from 'zod';

/**
 * FolderSchema — JSON-serializable API contract for Folder
 * Aligned with convex/schema.ts `folders` table and V0-api.md FolderMeta.
 *
 * Folders are server-synced via Convex. Arbitrary nesting via parentFolderId.
 */
export const FolderSchema = z
  .object({
    folderId: z.string().uuid(),
    ownerUserId: z.string(),
    parentFolderId: z.string().uuid().optional(), // self-ref; null = root
    name: z.string(),
    createdAt: z.number().int(), // Unix ms
    updatedAt: z.number().int(), // Unix ms
  })
  .strict();

export type Folder = z.infer<typeof FolderSchema>;
