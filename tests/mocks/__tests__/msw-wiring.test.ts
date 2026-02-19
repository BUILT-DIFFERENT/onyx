// tests/mocks/__tests__/msw-wiring.test.ts
// Proof-of-wiring test to verify MSW intercepts Convex HTTP API requests
import { describe, it, expect } from 'vitest';

describe('MSW wiring', () => {
  it('intercepts Convex query requests', async () => {
    const response = await fetch('https://test.convex.cloud/api/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: 'functions/notes:list',
        args: {},
        format: 'json',
      }),
    });

    const data = (await response.json()) as {
      status: string;
      value: Array<{ noteId: string }>;
    };
    expect(data.status).toBe('success');
    expect(data.value).toHaveLength(1);
    expect(data.value[0].noteId).toBe('550e8400-e29b-41d4-a716-446655440000');
  });

  it('returns error for unknown paths', async () => {
    const response = await fetch('https://test.convex.cloud/api/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: 'unknown:function',
        args: {},
        format: 'json',
      }),
    });

    const data = (await response.json()) as {
      status: string;
      errorMessage: string;
    };
    expect(data.status).toBe('error');
    expect(data.errorMessage).toContain('Unknown query path');
  });
});
