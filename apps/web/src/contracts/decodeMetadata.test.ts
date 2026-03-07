import { describe, expect, it } from 'vitest';
import { decodeWebMetadata } from './decodeMetadata';

const basePayload = {
  notebook: {
    notebookId: '550e8400-e29b-41d4-a716-446655440000',
    ownerUserId: 'user_2abc123def456',
    title: 'Web decode sample',
    coverColor: '#6366F1',
    isFavorite: false,
    notebookMode: 'paged',
    createdAt: 1708300800000,
    updatedAt: 1708300800000,
  },
};

describe('decodeWebMetadata', () => {
  it('decodes valid page objects and preserves unknown kinds', () => {
    const result = decodeWebMetadata({
      ...basePayload,
      pageObjects: [
        {
          objectId: '880e8400-e29b-41d4-a716-446655440008',
          pageId: '660e8400-e29b-41d4-a716-446655440001',
          notebookId: '550e8400-e29b-41d4-a716-446655440000',
          kind: 'shape',
          zIndex: 2,
          x: 10,
          y: 20,
          width: 100,
          height: 60,
          rotationDeg: 0,
          payload: { shapeType: 'rectangle' },
          createdAt: 1708300810000,
          updatedAt: 1708300810000,
        },
        {
          objectId: '980e8400-e29b-41d4-a716-446655440009',
          pageId: '660e8400-e29b-41d4-a716-446655440001',
          notebookId: '550e8400-e29b-41d4-a716-446655440000',
          kind: 'diagramWidget',
          arbitrary: true,
        },
      ],
    });

    expect(result.pageObjects).toHaveLength(1);
    expect(result.pageObjects[0]?.kind).toBe('shape');
    expect(result.unknownPageObjects).toHaveLength(1);
    expect(result.unknownPageObjects[0]?.kind).toBe('diagramWidget');
    expect(result.reports.pageObjects.decodedCount).toBe(1);
    expect(result.reports.pageObjects.skippedInvalidCount).toBe(0);
  });

  it('skips invalid known-kind objects without failing payload decode', () => {
    const result = decodeWebMetadata({
      ...basePayload,
      pageObjects: [
        {
          objectId: 'a80e8400-e29b-41d4-a716-446655440010',
          pageId: '660e8400-e29b-41d4-a716-446655440001',
          notebookId: '550e8400-e29b-41d4-a716-446655440000',
          kind: 'shape',
          zIndex: 1,
          x: 0,
          y: 0,
          width: 10,
          height: 10,
          rotationDeg: 0,
          payload: { assetId: 'wrong-payload' },
          createdAt: 1708300812000,
          updatedAt: 1708300812000,
        },
      ],
    });

    expect(result.notebook.title).toBe('Web decode sample');
    expect(result.pageObjects).toHaveLength(0);
    expect(result.reports.pageObjects.skippedInvalidCount).toBe(1);
  });

  it('decodes search texts and exports, skipping invalid entries', () => {
    const result = decodeWebMetadata({
      ...basePayload,
      searchTexts: [
        {
          notebookId: '550e8400-e29b-41d4-a716-446655440000',
          pageId: '660e8400-e29b-41d4-a716-446655440001',
          recognizedText: 'meeting notes',
          source: 'handwriting',
          extractedAt: 1708387200000,
        },
        {
          source: 'invalid',
        },
      ],
      exports: [
        {
          notebookId: '550e8400-e29b-41d4-a716-446655440000',
          exportAssetId: 'c30e8400-e29b-41d4-a716-446655440400',
          mode: 'flattened',
          createdByUserId: 'user_2abc123def456',
          createdAt: 1708387200000,
        },
      ],
    });

    expect(result.searchTexts).toHaveLength(1);
    expect(result.reports.searchTexts.skippedInvalidCount).toBe(1);
    expect(result.exports).toHaveLength(1);
  });

  it('throws on invalid notebook payload', () => {
    expect(() =>
      decodeWebMetadata({
        notebook: { notebookId: 'not-a-uuid' },
      }),
    ).toThrow('Invalid notebook payload');
  });
});
