import { z } from 'zod';

/**
 * NoteSchema - JSON-serializable API contract for Note
 * Canonical sync API contracts live under packages/validation/src/schemas and tests/contracts/fixtures.
 *
 * Note: folderId is intentionally excluded - it's a local-only field
 * deletedAt uses "absent OR number" semantics - omit field if not deleted
 */
export const NoteSchema = z
  .object({
    noteId: z.string().uuid(),
    ownerUserId: z.string(),
    title: z.string(),
    createdAt: z.number().int(), // Unix ms
    updatedAt: z.number().int(), // Unix ms
    deletedAt: z.number().int().optional(), // Absent = not deleted
  })
  .strict();

export type Note = z.infer<typeof NoteSchema>;
