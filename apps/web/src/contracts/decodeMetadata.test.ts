import { describe, expect, it } from 'vitest';
import { decodeWebMetadata } from './decodeMetadata';

const basePayload = {
  note: {
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    ownerUserId: 'user_123',
    title: 'Web decode sample',
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
          noteId: '550e8400-e29b-41d4-a716-446655440000',
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
          noteId: '550e8400-e29b-41d4-a716-446655440000',
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
          noteId: '550e8400-e29b-41d4-a716-446655440000',
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

    expect(result.note.title).toBe('Web decode sample');
    expect(result.pageObjects).toHaveLength(0);
    expect(result.reports.pageObjects.skippedInvalidCount).toBe(1);
  });

  it('decodes feature metadata arrays and skips invalid entries', () => {
    const result = decodeWebMetadata({
      ...basePayload,
      gestureSettings: [
        {
          profileId: '191e8400-e29b-41d4-a716-446655440018',
          ownerUserId: 'user_123',
          singleFingerMode: 'PAN',
          doubleFingerMode: 'ZOOM_PAN',
          stylusPrimaryAction: 'ERASER_HOLD',
          stylusSecondaryAction: 'ERASER_TOGGLE',
          stylusLongHoldAction: 'NO_ACTION',
          doubleTapZoomAction: 'CYCLE_PRESET',
          doubleTapZoomPointerMode: 'FINGER_ONLY',
          twoFingerTapAction: 'UNDO',
          threeFingerTapAction: 'REDO',
          latencyOptimizationMode: 'NORMAL',
          updatedAt: 1708300817000,
        },
        { profileId: 'bad-id' },
      ],
      templateScopes: [
        {
          scopeId: '291e8400-e29b-41d4-a716-446655440019',
          noteId: '550e8400-e29b-41d4-a716-446655440000',
          backgroundKind: 'grid',
          spacing: 24,
          colorHex: '#E0E0E0',
          applyScope: 'allPages',
          updatedAt: 1708300819000,
        },
      ],
      exportMetadata: [
        {
          exportId: '391e8400-e29b-41d4-a716-446655440020',
          noteId: '550e8400-e29b-41d4-a716-446655440000',
          ownerUserId: 'user_123',
          format: 'pdf',
          mode: 'flattened',
          status: 'queued',
          requestedAt: 1708300820000,
        },
      ],
      searchIndexTokens: [
        {
          tokenId: 'f80e8400-e29b-41d4-a716-446655440015',
          noteId: '550e8400-e29b-41d4-a716-446655440000',
          pageId: '660e8400-e29b-41d4-a716-446655440001',
          token: 'meeting',
          source: 'handwriting',
          bounds: {
            x: 10,
            y: 10,
            width: 20,
            height: 10,
          },
          indexVersion: 1,
          mergeKey: 'k1',
          sourceRevision: 0,
          sourceUpdatedAt: 1708300821000,
          indexedAt: 1708300822000,
          payload: {},
        },
        {
          source: 'pdfOcr',
        },
      ],
    });

    expect(result.gestureSettings).toHaveLength(1);
    expect(result.reports.gestureSettings.skippedInvalidCount).toBe(1);
    expect(result.templateScopes).toHaveLength(1);
    expect(result.exportMetadata).toHaveLength(1);
    expect(result.searchIndexTokens).toHaveLength(1);
    expect(result.reports.searchIndexTokens.skippedInvalidCount).toBe(1);
  });

  it('throws on invalid note payload', () => {
    expect(() =>
      decodeWebMetadata({
        note: { noteId: 'not-a-uuid' },
      }),
    ).toThrow('Invalid note payload');
  });
});
