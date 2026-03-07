import { describe, it, expect } from 'vitest';
import { NotebookSchema } from '../schemas/notebook';
import { PageSchema } from '../schemas/page';
import { PageObjectSchema } from '../schemas/pageObject';
import { ExportSchema } from '../schemas/export';
import { SearchTextSchema } from '../schemas/searchText';
import { StrokeMetaSchema } from '../schemas/stroke';
import { FolderSchema } from '../schemas/folder';
import { CrdtUpdateSchema } from '../schemas/crdtUpdate';
import { ShareSchema } from '../schemas/share';
import { PublicLinkSchema } from '../schemas/publicLink';
import { AssetSchema } from '../schemas/asset';
import { PresenceSchema } from '../schemas/presence';

// ============================================================================
// NotebookSchema Tests
// ============================================================================

describe('NotebookSchema', () => {
  const validNotebook = {
    notebookId: '550e8400-e29b-41d4-a716-446655440000',
    ownerUserId: 'user_2abc123def456',
    title: 'Test Notebook',
    coverColor: '#6366F1',
    isFavorite: false,
    notebookMode: 'paged' as const,
    createdAt: 1708300800000,
    updatedAt: 1708300800000,
  };

  it('parses valid notebook data', () => {
    const result = NotebookSchema.parse(validNotebook);
    expect(result.notebookId).toBe(validNotebook.notebookId);
    expect(result.ownerUserId).toBe(validNotebook.ownerUserId);
    expect(result.title).toBe(validNotebook.title);
    expect(result.coverColor).toBe('#6366F1');
    expect(result.isFavorite).toBe(false);
    expect(result.notebookMode).toBe('paged');
  });

  it('rejects invalid UUID for notebookId', () => {
    const invalid = { ...validNotebook, notebookId: 'not-a-uuid' };
    expect(() => NotebookSchema.parse(invalid)).toThrow();
  });

  it('rejects missing required fields', () => {
    const { notebookId: removed, ...missing } = validNotebook;
    expect(removed).toBeDefined();
    expect(() => NotebookSchema.parse(missing)).toThrow();
  });

  it('handles deletedAt optional field', () => {
    const result = NotebookSchema.parse(validNotebook);
    expect(result.deletedAt).toBeUndefined();

    const deleted = { ...validNotebook, deletedAt: 1708387200000 };
    const deletedResult = NotebookSchema.parse(deleted);
    expect(deletedResult.deletedAt).toBe(1708387200000);
  });

  it('rejects non-integer timestamps', () => {
    const invalid = { ...validNotebook, createdAt: 1708300800.5 };
    expect(() => NotebookSchema.parse(invalid)).toThrow();
  });

  it('accepts optional fields', () => {
    const withOptionals = {
      ...validNotebook,
      folderId: 'a50e8400-e29b-41d4-a716-446655440100',
      snapshotUrl: 'https://r2.example.com/snapshot.yjs',
      snapshotStateVector: 'AQEBAA==',
      hasPublicLink: true,
      shareCount: 3,
    };
    const result = NotebookSchema.parse(withOptionals);
    expect(result.folderId).toBe('a50e8400-e29b-41d4-a716-446655440100');
    expect(result.hasPublicLink).toBe(true);
    expect(result.shareCount).toBe(3);
  });
});

// ============================================================================
// PageSchema Tests
// ============================================================================

describe('PageSchema', () => {
  const validPage = {
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    notebookId: '550e8400-e29b-41d4-a716-446655440000',
    order: 0,
    templateType: 'blank' as const,
    templateDensity: 24,
    templateLineWidth: 1.0,
    backgroundColorHex: '#FFFFFF',
    widthPx: 794,
    heightPx: 1123,
    updatedAt: 1708300800000,
  };

  it('parses valid page data', () => {
    const result = PageSchema.parse(validPage);
    expect(result.pageId).toBe(validPage.pageId);
    expect(result.notebookId).toBe(validPage.notebookId);
    expect(result.order).toBe(0);
    expect(result.templateType).toBe('blank');
    expect(result.widthPx).toBe(794);
    expect(result.heightPx).toBe(1123);
  });

  it('accepts all valid templateType enum values', () => {
    const types = ['blank', 'lined', 'dotted', 'grid'] as const;
    for (const templateType of types) {
      const page = { ...validPage, templateType };
      const result = PageSchema.parse(page);
      expect(result.templateType).toBe(templateType);
    }
  });

  it('rejects invalid templateType', () => {
    const invalid = { ...validPage, templateType: 'invalid' };
    expect(() => PageSchema.parse(invalid)).toThrow();
  });

  it('handles optional PDF fields', () => {
    const result = PageSchema.parse(validPage);
    expect(result.pdfAssetId).toBeUndefined();

    const pdfPage = {
      ...validPage,
      pdfAssetId: 'asset_123',
      pdfPageNo: 0,
    };
    const pdfResult = PageSchema.parse(pdfPage);
    expect(pdfResult.pdfAssetId).toBe('asset_123');
    expect(pdfResult.pdfPageNo).toBe(0);
  });

  it('rejects missing required fields', () => {
    const { pageId: removed, ...missing } = validPage;
    expect(removed).toBeDefined();
    expect(() => PageSchema.parse(missing)).toThrow();
  });
});

// ============================================================================
// StrokeMetaSchema Tests
// ============================================================================

describe('StrokeMetaSchema', () => {
  const validStroke = {
    strokeId: '770e8400-e29b-41d4-a716-446655440002',
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    toolType: 'pen' as const,
    penType: 'ballpoint' as const,
    colorHex: '#000000',
    thickness: 2.0,
    opacity: 1.0,
    pressureSensitivity: 0.5,
    tiltSensitivity: 0.3,
    stabilization: 0.5,
    isShape: false,
    createdAt: 1708300800000,
  };

  it('parses valid stroke data', () => {
    const result = StrokeMetaSchema.parse(validStroke);
    expect(result.strokeId).toBe(validStroke.strokeId);
    expect(result.toolType).toBe('pen');
    expect(result.thickness).toBe(2.0);
    expect(result.isShape).toBe(false);
  });

  it('accepts valid toolType enum values', () => {
    const tools = ['pen', 'highlighter'] as const;
    for (const toolType of tools) {
      const stroke = { ...validStroke, toolType };
      const result = StrokeMetaSchema.parse(stroke);
      expect(result.toolType).toBe(toolType);
    }
  });

  it('rejects invalid toolType', () => {
    const invalid = { ...validStroke, toolType: 'invalid' };
    expect(() => StrokeMetaSchema.parse(invalid)).toThrow();
  });

  it('rejects extra fields (strict mode)', () => {
    const withExtra = { ...validStroke, createdLamport: 1 };
    expect(() => StrokeMetaSchema.parse(withExtra)).toThrow();
  });

  it('rejects missing required fields', () => {
    const { strokeId: removed, ...missing } = validStroke;
    expect(removed).toBeDefined();
    expect(() => StrokeMetaSchema.parse(missing)).toThrow();
  });

  it('validates penType is optional', () => {
    const { penType: removed, ...noPenType } = validStroke;
    expect(removed).toBeDefined();
    const result = StrokeMetaSchema.parse(noPenType);
    expect(result.penType).toBeUndefined();
  });
});

// ============================================================================
// PageObjectSchema Tests
// ============================================================================

describe('PageObjectSchema', () => {
  const validShapeObject = {
    objectId: '880e8400-e29b-41d4-a716-446655440008',
    pageId: '660e8400-e29b-41d4-a716-446655440001',
    notebookId: '550e8400-e29b-41d4-a716-446655440000',
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

  it('parses valid shape object', () => {
    const result = PageObjectSchema.parse(validShapeObject);
    expect(result.kind).toBe('shape');
    if (result.kind !== 'shape') throw new Error('Expected shape');
    expect(result.payload.shapeType).toBe('rectangle');
  });

  it('accepts image and text objects', () => {
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

  it('accepts attachment objects (audio/sticky/scan/file)', () => {
    const audioResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'b80e8400-e29b-41d4-a716-446655440011',
      kind: 'audio' as const,
      payload: { assetId: 'audio_asset_1', mimeType: 'audio/m4a', durationMs: 4200 },
    });
    expect(audioResult.kind).toBe('audio');

    const stickyResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'c80e8400-e29b-41d4-a716-446655440012',
      kind: 'sticky' as const,
      payload: { text: 'TODO', color: '#FFE082', style: 'rounded' as const },
    });
    expect(stickyResult.kind).toBe('sticky');

    const scanResult = PageObjectSchema.parse({
      ...validShapeObject,
      objectId: 'd80e8400-e29b-41d4-a716-446655440013',
      kind: 'scan' as const,
      payload: { assetId: 'scan_asset_1', pageCount: 2, source: 'camera' as const },
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

  it('rejects sync field (CRDT replaces per-object conflict metadata)', () => {
    expect(() =>
      PageObjectSchema.parse({
        ...validShapeObject,
        sync: {
          objectRevision: 7,
          parentRevision: 6,
          lastMutationId: 'android:note-editor:1708300807000',
          conflictPolicy: 'lastWriteWins',
        },
      }),
    ).toThrow();
  });
});

// ============================================================================
// FolderSchema Tests
// ============================================================================

describe('FolderSchema', () => {
  const validFolder = {
    folderId: 'a50e8400-e29b-41d4-a716-446655440100',
    ownerUserId: 'user_2abc123def456',
    name: 'STEM Courses',
    createdAt: 1708300800000,
    updatedAt: 1708300800000,
  };

  it('parses valid folder data', () => {
    const result = FolderSchema.parse(validFolder);
    expect(result.folderId).toBe(validFolder.folderId);
    expect(result.name).toBe('STEM Courses');
  });

  it('accepts optional parentFolderId', () => {
    const nested = { ...validFolder, parentFolderId: 'b50e8400-e29b-41d4-a716-446655440101' };
    const result = FolderSchema.parse(nested);
    expect(result.parentFolderId).toBe('b50e8400-e29b-41d4-a716-446655440101');
  });
});

// ============================================================================
// CrdtUpdateSchema Tests
// ============================================================================

describe('CrdtUpdateSchema', () => {
  it('parses valid CRDT update', () => {
    const result = CrdtUpdateSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      pageId: '660e8400-e29b-41d4-a716-446655440001',
      authorUserId: 'user_2abc123def456',
      deviceId: 'd10e8400-e29b-41d4-a716-446655440200',
      updateBinary: 'AQEBAA==',
      createdAt: 1708387200000,
    });
    expect(result.updateBinary).toBe('AQEBAA==');
  });
});

// ============================================================================
// ShareSchema Tests
// ============================================================================

describe('ShareSchema', () => {
  it('parses valid share', () => {
    const result = ShareSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      granteeUserId: 'user_grantee789',
      role: 'editor',
      grantedByUserId: 'user_2abc123def456',
      createdAt: 1708387200000,
    });
    expect(result.role).toBe('editor');
  });

  it('accepts optional revokedAt', () => {
    const result = ShareSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      granteeUserId: 'user_grantee789',
      role: 'viewer',
      grantedByUserId: 'user_2abc123def456',
      createdAt: 1708387200000,
      revokedAt: 1708400000000,
    });
    expect(result.revokedAt).toBe(1708400000000);
  });
});

// ============================================================================
// PublicLinkSchema Tests
// ============================================================================

describe('PublicLinkSchema', () => {
  it('parses valid public link', () => {
    const result = PublicLinkSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      linkToken: 'xK9mPqR2vW8nL4jYhT6bCf3dAeG7sUiO',
      createdByUserId: 'user_2abc123def456',
      createdAt: 1708387200000,
    });
    expect(result.linkToken).toBe('xK9mPqR2vW8nL4jYhT6bCf3dAeG7sUiO');
  });
});

// ============================================================================
// AssetSchema Tests
// ============================================================================

describe('AssetSchema', () => {
  it('parses valid asset', () => {
    const result = AssetSchema.parse({
      assetId: 'b20e8400-e29b-41d4-a716-446655440300',
      ownerUserId: 'user_2abc123def456',
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      type: 'pdf',
      r2Key: 'assets/user_2abc123def456/b20e8400.pdf',
      contentType: 'application/pdf',
      size: 2048576,
      confirmed: true,
      createdAt: 1708387200000,
    });
    expect(result.type).toBe('pdf');
    expect(result.confirmed).toBe(true);
  });

  it('accepts all valid type enum values', () => {
    const types = ['pdf', 'image', 'audio', 'snapshot', 'exportPdf', 'thumbnail'] as const;
    for (const type of types) {
      const result = AssetSchema.parse({
        assetId: 'b20e8400-e29b-41d4-a716-446655440300',
        ownerUserId: 'user_123',
        type,
        r2Key: `assets/${type}`,
        contentType: 'application/octet-stream',
        confirmed: false,
        createdAt: 1708387200000,
      });
      expect(result.type).toBe(type);
    }
  });
});

// ============================================================================
// PresenceSchema Tests
// ============================================================================

describe('PresenceSchema', () => {
  it('parses valid presence', () => {
    const result = PresenceSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      pageId: '660e8400-e29b-41d4-a716-446655440001',
      userId: 'user_2abc123def456',
      cursorX: 397.5,
      cursorY: 561.25,
      activeTool: 'pen',
      activeColor: '#000000',
      updatedAt: 1708387200000,
    });
    expect(result.cursorX).toBe(397.5);
    expect(result.activeTool).toBe('pen');
  });
});

// ============================================================================
// SearchTextSchema Tests
// ============================================================================

describe('SearchTextSchema', () => {
  it('parses valid handwriting search text', () => {
    const result = SearchTextSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      pageId: '660e8400-e29b-41d4-a716-446655440001',
      recognizedText: 'differential equations',
      source: 'handwriting',
      extractedAt: 1708387200000,
    });
    expect(result.source).toBe('handwriting');
    expect(result.recognizedText).toBe('differential equations');
  });

  it('parses valid pdfText search text', () => {
    const result = SearchTextSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      pageId: '660e8400-e29b-41d4-a716-446655440001',
      pdfText: 'Chapter 3: Integration',
      source: 'pdfText',
      extractedAt: 1708387200000,
    });
    expect(result.source).toBe('pdfText');
    expect(result.pdfText).toBe('Chapter 3: Integration');
  });
});

// ============================================================================
// ExportSchema Tests
// ============================================================================

describe('ExportSchema', () => {
  it('parses valid export', () => {
    const result = ExportSchema.parse({
      notebookId: '550e8400-e29b-41d4-a716-446655440000',
      exportAssetId: 'c30e8400-e29b-41d4-a716-446655440400',
      mode: 'flattened',
      createdByUserId: 'user_2abc123def456',
      createdAt: 1708387200000,
    });
    expect(result.mode).toBe('flattened');
  });

  it('rejects invalid mode', () => {
    expect(() =>
      ExportSchema.parse({
        notebookId: '550e8400-e29b-41d4-a716-446655440000',
        exportAssetId: 'c30e8400-e29b-41d4-a716-446655440400',
        mode: 'layered',
        createdByUserId: 'user_123',
        createdAt: 1708387200000,
      }),
    ).toThrow();
  });
});
