import { NoteSchema, type Note } from '@onyx/validation';

/**
 * Creates a test Note object with sensible defaults.
 * Override any field by passing a partial object.
 *
 * @param overrides - Partial Note fields to override defaults
 * @returns A valid Note object matching NoteSchema
 *
 * @example
 * const note = createTestNote({ title: 'My Custom Title' });
 * const deletedNote = createTestNote({ deletedAt: Date.now() });
 */
export function createTestNote(overrides?: Partial<Note>): Note {
  const defaults: Note = {
    noteId: crypto.randomUUID(),
    ownerUserId: 'test-user-123',
    title: 'Test Note',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    // deletedAt omitted (optional field - absent means not deleted)
  };

  return { ...defaults, ...overrides } as Note;
}
