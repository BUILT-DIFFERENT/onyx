import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { join } from 'path';
import {
  ExportMetadataSchema,
  GestureSettingsSchema,
  NoteSchema,
  PageObjectSchema,
  PageSchema,
  SearchIndexTokenSchema,
  StrokeSchema,
  TemplateScopeSchema,
} from '@onyx/validation';

const fixturesDir = join(__dirname, '../fixtures');

describe('Contract Fixtures Validation', () => {
  describe('NoteSchema', () => {
    it('validates note.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'note.fixture.json'), 'utf-8'));
      // Using .parse() throws on invalid schema
      const result = NoteSchema.parse(fixture);
      expect(result.noteId).toBeDefined();
      expect(result.ownerUserId).toBeDefined();
      expect(result.title).toBeDefined();
      expect(result.createdAt).toBeTypeOf('number');
      expect(result.updatedAt).toBeTypeOf('number');
      // deletedAt should be absent (not deleted)
      expect(result.deletedAt).toBeUndefined();
    });
  });

  describe('PageSchema', () => {
    it('validates page.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page.fixture.json'), 'utf-8'));
      // Using .parse() throws on invalid schema
      const result = PageSchema.parse(fixture);
      expect(result.pageId).toBeDefined();
      expect(result.noteId).toBeDefined();
      expect(result.kind).toBe('ink');
      expect(result.geometryKind).toBe('fixed');
      expect(result.width).toBeTypeOf('number');
      expect(result.height).toBeTypeOf('number');
      expect(result.unit).toBe('pt');
      expect(result.contentLamportMax).toBeTypeOf('number');
      expect(result.updatedAt).toBeTypeOf('number');
    });
  });

  describe('StrokeSchema', () => {
    it('validates stroke.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'stroke.fixture.json'), 'utf-8'));
      // Using .parse() throws on invalid schema
      const result = StrokeSchema.parse(fixture);
      expect(result.strokeId).toBeDefined();
      expect(result.pageId).toBeDefined();
      expect(result.style).toBeDefined();
      expect(result.style.tool).toBe('pen');
      expect(result.style.color).toBe('#000000');
      expect(result.style.lineStyle).toBe('solid');
      expect(result.style.baseWidth).toBeTypeOf('number');
      expect(result.bounds).toBeDefined();
      expect(result.bounds.x).toBeTypeOf('number');
      expect(result.bounds.y).toBeTypeOf('number');
      expect(result.bounds.w).toBeTypeOf('number');
      expect(result.bounds.h).toBeTypeOf('number');
      expect(result.createdAt).toBeTypeOf('number');
      expect(result.createdLamport).toBeTypeOf('number');
    });
  });

  describe('PageObjectSchema', () => {
    it('validates page-object-shape.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page-object-shape.fixture.json'), 'utf-8'));
      const result = PageObjectSchema.parse(fixture);
      expect(result.kind).toBe('shape');
      expect(result.payload.shapeType).toBe('rectangle');
      expect(result.zIndex).toBeTypeOf('number');
    });

    it('validates page-object-image.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page-object-image.fixture.json'), 'utf-8'));
      const result = PageObjectSchema.parse(fixture);
      expect(result.kind).toBe('image');
      if (result.kind !== 'image') throw new Error('Expected image object');
      expect(result.payload.mimeType).toBe('image/png');
    });

    it('validates page-object-text.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page-object-text.fixture.json'), 'utf-8'));
      const result = PageObjectSchema.parse(fixture);
      expect(result.kind).toBe('text');
      if (result.kind !== 'text') throw new Error('Expected text object');
      expect(result.payload.text).toBe('Meeting notes');
    });

    it('validates attachment object fixtures', () => {
      const kinds = ['audio', 'sticky', 'scan', 'file'] as const;
      for (const kind of kinds) {
        const fixture = JSON.parse(readFileSync(join(fixturesDir, `page-object-${kind}.fixture.json`), 'utf-8'));
        const result = PageObjectSchema.parse(fixture);
        expect(result.kind).toBe(kind);
      }
    });

    it('validates page-object-shape-conflict.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page-object-shape-conflict.fixture.json'), 'utf-8'));
      const result = PageObjectSchema.parse(fixture);
      expect(result.kind).toBe('shape');
      expect(result.sync?.objectRevision).toBe(7);
      expect(result.sync?.conflictPolicy).toBe('lastWriteWins');
    });
  });

  describe('SearchIndexTokenSchema', () => {
    it('validates search-index-handwriting-token.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'search-index-handwriting-token.fixture.json'), 'utf-8'));
      const result = SearchIndexTokenSchema.parse(fixture);
      expect(result.source).toBe('handwriting');
      expect(result.token).toBe('meeting');
      expect(result.indexVersion).toBeTypeOf('number');
    });

    it('validates search-index-pdf-ocr-token.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'search-index-pdf-ocr-token.fixture.json'), 'utf-8'));
      const result = SearchIndexTokenSchema.parse(fixture);
      expect(result.source).toBe('pdfOcr');
      expect(result.token).toBe('diagram');
      expect(result.indexVersion).toBeTypeOf('number');
    });
  });

  describe('Feature metadata schemas', () => {
    it('validates gesture-settings.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'gesture-settings.fixture.json'), 'utf-8'));
      const result = GestureSettingsSchema.parse(fixture);
      expect(result.singleFingerMode).toBe('PAN');
      expect(result.threeFingerTapAction).toBe('REDO');
    });

    it('validates template-scope.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'template-scope.fixture.json'), 'utf-8'));
      const result = TemplateScopeSchema.parse(fixture);
      expect(result.backgroundKind).toBe('grid');
      expect(result.applyScope).toBe('allPages');
    });

    it('validates export-metadata.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'export-metadata.fixture.json'), 'utf-8'));
      const result = ExportMetadataSchema.parse(fixture);
      expect(result.format).toBe('pdf');
      expect(result.mode).toBe('flattened');
    });
  });
});
