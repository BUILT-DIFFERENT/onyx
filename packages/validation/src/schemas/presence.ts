import { z } from 'zod';

/**
 * PresenceSchema — JSON-serializable API contract for Presence (collaborator cursors)
 * Aligned with convex/schema.ts `presence` table and V0-api.md PresenceEntry.
 *
 * Updated every 500ms while drawing; auto-expires after 10s of no updates.
 */
export const PresenceSchema = z
  .object({
    notebookId: z.string().uuid(),
    pageId: z.string().uuid(),
    userId: z.string(),
    cursorX: z.number(), // px, page-space
    cursorY: z.number(), // px, page-space
    activeTool: z.string(), // "pen" | "highlighter" | "eraser" | "lasso"
    activeColor: z.string().optional(), // hex
    updatedAt: z.number().int(), // Unix ms
  })
  .strict();

export type Presence = z.infer<typeof PresenceSchema>;
