import { type Notebook } from '@onyx/validation';

/**
 * Creates a test Notebook object with sensible defaults.
 * Override any field by passing a partial object.
 *
 * @param overrides - Partial Notebook fields to override defaults
 * @returns A valid Notebook object matching NotebookSchema
 *
 * @example
 * const notebook = createTestNotebook({ title: 'My Custom Title' });
 * const deletedNotebook = createTestNotebook({ deletedAt: Date.now() });
 */
export function createTestNotebook(overrides?: Partial<Notebook>): Notebook {
  const defaults: Notebook = {
    notebookId: crypto.randomUUID(),
    ownerUserId: 'test-user-123',
    title: 'Test Notebook',
    coverColor: '#6366F1',
    isFavorite: false,
    notebookMode: 'paged',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    // deletedAt omitted (optional field - absent means not deleted)
  };

  return { ...defaults, ...overrides } as Notebook;
}
