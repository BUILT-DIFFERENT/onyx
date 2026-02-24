import { describe, it, expect } from 'vitest';
import { NoteSchema } from '../schemas/note';
import { PageSchema } from '../schemas/page';
import { PageObjectSchema } from '../schemas/pageObject';
import {
  ExportMetadataSchema,
  GestureSettingsSchema,
  TemplateScopeSchema,
} from '../schemas/featureMetadata';
import { SearchIndexTokenSchema } from '../schemas/searchIndexToken';
import { StrokeSchema } from '../schemas/stroke';

// ============================================================================
// NoteSchema Tests
// ============================================================================

describe('NoteSchema', () => {
  const validNote = {
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    ownerUserId: 'user_123',
    title: 'Test Note',
    createdAt: 1708300800000,
    updatedAt: 1708300800000,
  };

  it('parses valid note data', () => {
    const result = NoteSchema.parse(validNote);
    expect(result.noteId).toBe(validNote.noteId);
    expect(result.ownerUserId).toBe(validNote.ownerUserId);
    expect(result.title).toBe(validNote.title);
    expect(result.createdAt).toBe(validNote.createdAt);
    expect(result.updatedAt).toBe(validNote.updatedAt);
  });

  it('rejects invalid UUID for noteId', () => {
    const invalidNote = { ...validNote, noteId: 'not-a-uuid' };
    expect(() => NoteSchema.parse(invalidNote)).toThrow();
  });

  it('rejects missing required fields', () => {
    const { noteId: removedNoteId, ...missingNoteId } = validNote;
    expect(removedNoteId).toBeDefined();
    expect(() => NoteSchema.parse(missingNoteId)).toThrow();

    const { ownerUserId: removedOwnerUserId, ...missingOwner } = validNote;
    expect(removedOwnerUserId).toBeDefined();
    expect(() => NoteSchema.parse(missingOwner)).toThrow();

    const { title: removedTitle, ...missingTitle } = validNote;
    expect(removedTitle).toBeDefined();
    expect(() => NoteSchema.parse(missingTitle)).toThrow();
  });

  it('rejects extra fields like folderId (strict mode)', () => {
    const noteWithExtraField = {
      ...validNote,
      folderId: 'folder_123', // This is a local-only field, should be rejected
    };
    expect(() => NoteSchema.parse(noteWithExtraField)).toThrow();
  });

  it('handles deletedAt optional field - absent means not deleted', () => {
    const result = NoteSchema.parse(validNote);
    expect(result.deletedAt).toBeUndefined();
  });

  it('handles deletedAt optional field - present means deleted', () => {
    const deletedNote = {
      ...validNote,
      deletedAt: 1708387200000,
    };
    const result = NoteSchema.parse(deletedNote);
    expect(result.deletedAt).toBe(1708387200000);
  });

  it('rejects non-integer timestamps', () => {
    const invalidNote = { ...validNote, createdAt: 1708300800.5 };
    expect(() => NoteSchema.parse(invalidNote)).toThrow();
  });

  it('rejects non-string title', () => {
    const invalidNote = { ...validNote, title: 123 };
    expect(() => NoteSchema.parse(invalidNote)).toThrow();
  });
});

// ============================================================================
// PageSchema Tests
// ============================================================================

describe('PageSchema', () => {
  const validPage = {
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    kind: 'ink' as const,
    geometryKind: 'fixed' as const,
    width: 612,
    height: 792,
    unit: 'pt' as const,
    contentLamportMax: 0,
    updatedAt: 1708300800000,
  };

  it('parses valid page data', () => {
    const result = PageSchema.parse(validPage);
    expect(result.pageId).toBe(validPage.pageId);
    expect(result.noteId).toBe(validPage.noteId);
    expect(result.kind).toBe('ink');
    expect(result.geometryKind).toBe('fixed');
    expect(result.width).toBe(validPage.width);
    expect(result.height).toBe(validPage.height);
    expect(result.unit).toBe('pt');
  });

  it('accepts all valid kind enum values', () => {
    const kinds = ['ink', 'pdf', 'mixed', 'infinite'] as const;
    for (const kind of kinds) {
      const page = { ...validPage, kind };
      const result = PageSchema.parse(page);
      expect(result.kind).toBe(kind);
    }
  });

  it('accepts all valid geometryKind enum values', () => {
    const geometryKinds = ['fixed', 'infinite'] as const;
    for (const geometryKind of geometryKinds) {
      const page = { ...validPage, geometryKind };
      const result = PageSchema.parse(page);
      expect(result.geometryKind).toBe(geometryKind);
    }
  });

  it('rejects invalid kind enum value', () => {
    const invalidPage = { ...validPage, kind: 'invalid' };
    expect(() => PageSchema.parse(invalidPage)).toThrow();
  });

  it('rejects invalid geometryKind enum value', () => {
    const invalidPage = { ...validPage, geometryKind: 'invalid' };
    expect(() => PageSchema.parse(invalidPage)).toThrow();
  });

  it('rejects extra fields like indexInNote (strict mode)', () => {
    const pageWithExtraField = {
      ...validPage,
      indexInNote: 0, // This is a local-only field, should be rejected
    };
    expect(() => PageSchema.parse(pageWithExtraField)).toThrow();
  });

  it('handles optional nullable pdfAssetId - absent', () => {
    const result = PageSchema.parse(validPage);
    expect(result.pdfAssetId).toBeUndefined();
  });

  it('handles optional nullable pdfAssetId - null', () => {
    const pageWithNullPdf = { ...validPage, pdfAssetId: null };
    const result = PageSchema.parse(pageWithNullPdf);
    expect(result.pdfAssetId).toBeNull();
  });

  it('handles optional nullable pdfAssetId - present', () => {
    const pdfPage = {
      ...validPage,
      kind: 'pdf' as const,
      pdfAssetId: 'asset_123',
      pdfPageNo: 1,
    };
    const result = PageSchema.parse(pdfPage);
    expect(result.pdfAssetId).toBe('asset_123');
    expect(result.pdfPageNo).toBe(1);
  });

  it('rejects non-literal unit value', () => {
    const invalidPage = { ...validPage, unit: 'in' };
    expect(() => PageSchema.parse(invalidPage)).toThrow();
  });

  it('rejects missing required fields', () => {
    const { pageId: removedPageId, ...missingPageId } = validPage;
    expect(removedPageId).toBeDefined();
    expect(() => PageSchema.parse(missingPageId)).toThrow();

    const { noteId: removedNoteId, ...missingNoteId } = validPage;
    expect(removedNoteId).toBeDefined();
    expect(() => PageSchema.parse(missingNoteId)).toThrow();
  });
});

// ============================================================================
// StrokeSchema Tests
// ============================================================================

describe('PageObjectSchema', () => {
  const validShapeObject = {
    objectId: '880e8400-e29b-41d4-a716-446655440008',
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    kind: 'shape' as const,
    zIndex: 3,
    x: 40,
    y: 80,
    width: 120,
    height: 90,
    rotationDeg: 0,
    payload: {
      shapeType: 'rectangle' as const,
      strokeColor: '#1E88E5',
      strokeWidth: 2,
    },
    createdAt: 1708300800000,
    updatedAt: 1708300800000,
  };

  it('parses valid shape object payload', () => {
    const result = PageObjectSchema.parse(validShapeObject);
    expect(result.kind).toBe('shape');
    if (result.kind !== 'shape') {
      throw new Error('Expected shape object');
    }
    expect(result.payload.shapeType).toBe('rectangle');
  });

  it('accepts image and text payload scaffolding', () => {
    const imageResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: '980e8400-e29b-41d4-a716-446655440009',
      kind: 'image' as const,
      payload: {
        assetId: null,
        mimeType: 'image/png',
        sourceUri: 'content://media/external/images/media/1',
        displayName: 'IMG_0001.png',
      },
    });
    expect(imageResult.kind).toBe('image');

    const textResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'a80e8400-e29b-41d4-a716-446655440010',
      kind: 'text' as const,
      payload: {
        text: 'Hello',
        align: 'start' as const,
        color: '#111111',
        fontSizeSp: 16,
        bold: false,
        italic: false,
        underline: false,
      },
    });
    expect(textResult.kind).toBe('text');
  });

  it('accepts attachment object payload scaffolding for audio/sticky/scan/file', () => {
    const audioResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'b80e8400-e29b-41d4-a716-446655440011',
      kind: 'audio' as const,
      payload: {
        assetId: 'audio_asset_1',
        mimeType: 'audio/m4a',
        durationMs: 4200,
      },
    });
    expect(audioResult.kind).toBe('audio');

    const stickyResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'c80e8400-e29b-41d4-a716-446655440012',
      kind: 'sticky' as const,
      payload: {
        text: 'TODO',
        color: '#FFE082',
        style: 'rounded' as const,
      },
    });
    expect(stickyResult.kind).toBe('sticky');

    const scanResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'd80e8400-e29b-41d4-a716-446655440013',
      kind: 'scan' as const,
      payload: {
        assetId: 'scan_asset_1',
        pageCount: 2,
        source: 'camera' as const,
      },
    });
    expect(scanResult.kind).toBe('scan');

    const fileResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'e80e8400-e29b-41d4-a716-446655440014',
      kind: 'file' as const,
      payload: {
        assetId: 'file_asset_1',
        fileName: 'spec.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 204857,
      },
    });
    expect(fileResult.kind).toBe('file');
  });

  it('rejects payload mismatch for kind', () => {
    expect(() =>
      PageObjectSchema.parse({
        ...validShapeObject,
        kind: 'shape' as const,
        payload: { assetId: 'asset-1' },
      }),
    ).toThrow();
  });

  it('accepts optional sync conflict metadata scaffold', () => {
    const result = PageObjectSchema.parse({
      ...validShapeObject,
      sync: {
        objectRevision: 7,
        parentRevision: 6,
        lastMutationId: 'android:note-editor:1708300807000',
        conflictPolicy: 'lastWriteWins' as const,
      },
    });
    expect(result.sync?.objectRevision).toBe(7);
    expect(result.sync?.conflictPolicy).toBe('lastWriteWins');
  });
});

describe('StrokeSchema', () => {
  const validStyle = {
    tool: 'pen' as const,
    color: '#000000',
    baseWidth: 1.5,
    minWidthFactor: 0.85,
    maxWidthFactor: 1.15,
    lineStyle: 'solid' as const,
    nibRotation: false,
  };

  const validBounds = {
    x: 100,
    y: 200,
    w: 50,
    h: 30,
  };

  const validStroke = {
    strokeId: '770e8400-e29b-41d4-a716-446655440002',
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    style: validStyle,
    bounds: validBounds,
    createdAt: 1708300800000,
    createdLamport: 1,
  };

  it('parses valid stroke data', () => {
    const result = StrokeSchema.parse(validStroke);
    expect(result.strokeId).toBe(validStroke.strokeId);
    expect(result.pageId).toBe(validStroke.pageId);
    expect(result.style.tool).toBe('pen');
    expect(result.bounds.x).toBe(100);
    expect(result.createdAt).toBe(validStroke.createdAt);
    expect(result.createdLamport).toBe(1);
  });

  it('accepts all valid tool enum values', () => {
    const tools = ['pen', 'highlighter', 'eraser'] as const;
    for (const tool of tools) {
      const stroke = {
        ...validStroke,
        style: { ...validStyle, tool },
      };
      const result = StrokeSchema.parse(stroke);
      expect(result.style.tool).toBe(tool);
    }
  });

  it('rejects invalid tool enum value', () => {
    const invalidStroke = {
      ...validStroke,
      style: { ...validStyle, tool: 'invalid' },
    };
    expect(() => StrokeSchema.parse(invalidStroke)).toThrow();
  });

  it('rejects extra fields like strokeData (strict mode)', () => {
    const strokeWithExtraField = {
      ...validStroke,
      strokeData: new Uint8Array([1, 2, 3]), // ByteArray, not JSON-serializable
    };
    expect(() => StrokeSchema.parse(strokeWithExtraField)).toThrow();
  });

  it('rejects extra fields like points (strict mode)', () => {
    const strokeWithExtraField = {
      ...validStroke,
      points: [{ x: 0, y: 0 }], // Deferred to sync implementation
    };
    expect(() => StrokeSchema.parse(strokeWithExtraField)).toThrow();
  });

  it('validates nested style object', () => {
    const strokeWithInvalidStyle = {
      ...validStroke,
      style: { ...validStyle, baseWidth: 'not-a-number' },
    };
    expect(() => StrokeSchema.parse(strokeWithInvalidStyle)).toThrow();
  });

  it('validates nested bounds object', () => {
    const strokeWithInvalidBounds = {
      ...validStroke,
      bounds: { x: 100, y: 200 }, // Missing w and h
    };
    expect(() => StrokeSchema.parse(strokeWithInvalidBounds)).toThrow();
  });

  it('handles optional color in style', () => {
    const styleWithoutColor = {
      tool: 'pen' as const,
      baseWidth: 1.5,
      minWidthFactor: 0.85,
      maxWidthFactor: 1.15,
      lineStyle: 'dashed' as const,
      nibRotation: false,
    };
    const stroke = { ...validStroke, style: styleWithoutColor };
    const result = StrokeSchema.parse(stroke);
    expect(result.style.color).toBeUndefined();
  });

  it('rejects missing required fields', () => {
    const { strokeId: removedStrokeId, ...missingStrokeId } = validStroke;
    expect(removedStrokeId).toBeDefined();
    expect(() => StrokeSchema.parse(missingStrokeId)).toThrow();

    const { pageId: removedPageId, ...missingPageId } = validStroke;
    expect(removedPageId).toBeDefined();
    expect(() => StrokeSchema.parse(missingPageId)).toThrow();

    const { style: removedStyle, ...missingStyle } = validStroke;
    expect(removedStyle).toBeDefined();
    expect(() => StrokeSchema.parse(missingStyle)).toThrow();

    const { bounds: removedBounds, ...missingBounds } = validStroke;
    expect(removedBounds).toBeDefined();
    expect(() => StrokeSchema.parse(missingBounds)).toThrow();
  });

  it('rejects invalid UUID for strokeId', () => {
    const invalidStroke = { ...validStroke, strokeId: 'not-a-uuid' };
    expect(() => StrokeSchema.parse(invalidStroke)).toThrow();
  });

  it('rejects non-integer createdLamport', () => {
    const invalidStroke = { ...validStroke, createdLamport: 1.5 };
    expect(() => StrokeSchema.parse(invalidStroke)).toThrow();
  });
});

describe('SearchIndexTokenSchema', () => {
  const validHandwritingToken = {
    tokenId: 'f80e8400-e29b-41d4-a716-446655440015',
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    token: 'meeting',
    displayText: 'Meeting',
    source: 'handwriting' as const,
    bounds: {
      x: 120,
      y: 200,
      width: 88,
      height: 24,
    },
    indexVersion: 1,
    mergeKey:
      'note:550e8400-e29b-41d4-a716-446655440000|page:660e8400-e29b-41d4-a716-446655440001|src:handwriting|token:meeting',
    sourceRevision: 3,
    sourceUpdatedAt: 1708300805000,
    indexedAt: 1708300807000,
    payload: {
      recognitionProvider: 'myscript' as const,
      confidence: 0.93,
      strokeIds: ['770e8400-e29b-41d4-a716-446655440002'],
    },
  };

  it('parses valid handwriting token payload', () => {
    const result = SearchIndexTokenSchema.parse(validHandwritingToken);
    expect(result.source).toBe('handwriting');
    if (result.source !== 'handwriting') {
      throw new Error('Expected handwriting token');
    }
    expect(result.payload.confidence).toBe(0.93);
  });

  it('parses valid pdf ocr token payload', () => {
    const result = SearchIndexTokenSchema.parse({
      ...validHandwritingToken,
      tokenId: '081e8400-e29b-41d4-a716-446655440016',
      source: 'pdfOcr' as const,
      payload: {
        pdfAssetId: 'pdf_asset_1',
        pdfPageNo: 4,
        ocrEngine: 'pdfium' as const,
        confidence: 0.86,
      },
    });
    expect(result.source).toBe('pdfOcr');
    if (result.source !== 'pdfOcr') {
      throw new Error('Expected pdf ocr token');
    }
    expect(result.payload.pdfPageNo).toBe(4);
  });

  it('rejects payload mismatch for source', () => {
    expect(() =>
      SearchIndexTokenSchema.parse({
        ...validHandwritingToken,
        source: 'handwriting' as const,
        payload: {
          pdfAssetId: 'pdf_asset_1',
          pdfPageNo: 1,
        },
      }),
    ).toThrow();
  });

  it('rejects non-positive indexVersion', () => {
    expect(() =>
      SearchIndexTokenSchema.parse({
        ...validHandwritingToken,
        indexVersion: 0,
      }),
    ).toThrow();
  });
});

describe('GestureSettingsSchema', () => {
  const validSettings = {
    profileId: '191e8400-e29b-41d4-a716-446655440018',
    ownerUserId: 'user_123',
    singleFingerMode: 'PAN' as const,
    doubleFingerMode: 'ZOOM_PAN' as const,
    stylusPrimaryAction: 'ERASER_HOLD' as const,
    stylusSecondaryAction: 'ERASER_TOGGLE' as const,
    stylusLongHoldAction: 'NO_ACTION' as const,
    doubleTapZoomAction: 'CYCLE_PRESET' as const,
    doubleTapZoomPointerMode: 'FINGER_ONLY' as const,
    twoFingerTapAction: 'UNDO' as const,
    threeFingerTapAction: 'REDO' as const,
    latencyOptimizationMode: 'NORMAL' as const,
    updatedAt: 1708300817000,
  };

  it('parses valid gesture settings', () => {
    const result = GestureSettingsSchema.parse(validSettings);
    expect(result.twoFingerTapAction).toBe('UNDO');
  });

  it('rejects invalid enum values', () => {
    expect(() =>
      GestureSettingsSchema.parse({
        ...validSettings,
        singleFingerMode: 'INVALID',
      }),
    ).toThrow();
  });
});

describe('TemplateScopeSchema', () => {
  const validTemplateScope = {
    scopeId: '291e8400-e29b-41d4-a716-446655440019',
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    templateId: null,
    backgroundKind: 'grid' as const,
    spacing: 24,
    colorHex: '#E0E0E0',
    paperSize: 'a4' as const,
    lineWidth: 1.2,
    category: 'default' as const,
    applyScope: 'allPages' as const,
    updatedAt: 1708300819000,
  };

  it('parses valid template scope metadata', () => {
    const result = TemplateScopeSchema.parse(validTemplateScope);
    expect(result.applyScope).toBe('allPages');
  });

  it('rejects invalid color hex', () => {
    expect(() =>
      TemplateScopeSchema.parse({
        ...validTemplateScope,
        colorHex: 'blue',
      }),
    ).toThrow();
  });
});

describe('ExportMetadataSchema', () => {
  const validExport = {
    exportId: '391e8400-e29b-41d4-a716-446655440020',
    noteId: '550e8400-e29b-41d4-a716-446655440000',
    ownerUserId: 'user_123',
    format: 'pdf' as const,
    mode: 'flattened' as const,
    status: 'queued' as const,
    requestedAt: 1708300820000,
  };

  it('parses valid export metadata', () => {
    const result = ExportMetadataSchema.parse(validExport);
    expect(result.mode).toBe('flattened');
  });

  it('rejects invalid status', () => {
    expect(() =>
      ExportMetadataSchema.parse({
        ...validExport,
        status: 'done',
      }),
    ).toThrow();
  });
});
