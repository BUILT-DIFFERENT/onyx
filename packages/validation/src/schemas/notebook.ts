import { z } from 'zod';

/**
 * NotebookSchema — JSON-serializable API contract for Notebook
 * Replaces the old NoteSchema. Aligned with convex/schema.ts `notebooks` table and V0-api.md NotebookMeta.
 *
 * Canonical sync API contracts live under packages/validation/src/schemas and tests/contracts/fixtures.
 */
export const NotebookSchema = z
  .object({
    notebookId: z.string().uuid(),
    ownerUserId: z.string(),
    folderId: z.string().uuid().optional(), // FK → folders; null = root
    title: z.string(),
    coverColor: z.string(), // hex
    isFavorite: z.boolean(),
    notebookMode: z.enum(['paged', 'infinite_canvas']),
    createdAt: z.number().int(), // Unix ms
    updatedAt: z.number().int(), // Unix ms
    deletedAt: z.number().int().optional(), // Absent = not deleted; soft delete

    // Notebook-level Yjs snapshot (R2)
    snapshotUrl: z.string().optional(),
    snapshotStateVector: z.string().optional(), // base64-encoded

    // First-page preview (for library grid)
    previewThumbAssetId: z.string().optional(),
    previewPageAssetId: z.string().optional(),
    previewUpdatedAt: z.number().int().optional(),

    // Sharing metadata (denormalized for list queries)
    hasPublicLink: z.boolean().optional(),
    shareCount: z.number().int().optional(),
  })
  .strict();

export type Notebook = z.infer<typeof NotebookSchema>;
