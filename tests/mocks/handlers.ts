// tests/mocks/handlers.ts
// MSW handlers for Convex HTTP API mocking
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('*/api/query', async ({ request }) => {
    const body = (await request.json()) as { path: string; args: unknown };

    // Route based on exact function path
    if (body.path === 'functions/notes:list') {
      return HttpResponse.json({
        status: 'success',
        value: [
          {
            noteId: '550e8400-e29b-41d4-a716-446655440000',
            ownerUserId: 'user_test123',
            title: 'Test Note',
            createdAt: 1708300800000,
            updatedAt: 1708300800000,
            // deletedAt omitted = not deleted (absent OR number semantics)
          },
        ],
      });
    }

    // Fail explicitly for unknown paths (helps debugging)
    return HttpResponse.json(
      { status: 'error', errorMessage: `Unknown query path: ${body.path}` },
      { status: 400 },
    );
  }),

  http.post('*/api/mutation', async ({ request }) => {
    const _body = (await request.json()) as { path: string; args: unknown };
    // Add mutation handlers as needed
    return HttpResponse.json({ status: 'success', value: null });
  }),
];
