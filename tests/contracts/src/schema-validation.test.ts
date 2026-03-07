import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { join } from 'path';
import {
  ExportSchema,
  NotebookSchema,
  PageObjectSchema,
  PageSchema,
  SearchTextSchema,
  StrokeMetaSchema,
} from '@onyx/validation';

const fixturesDir = join(__dirname, '../fixtures');

describe('Contract Fixtures Validation', () => {
  describe('NotebookSchema', () => {
    it('validates notebook.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'notebook.fixture.json'), 'utf-8'));
      const result = NotebookSchema.parse(fixture);
      expect(result.notebookId).toBeDefined();
      expect(result.ownerUserId).toBeDefined();
      expect(result.title).toBeDefined();
      expect(result.coverColor).toBeDefined();
      expect(result.isFavorite).toBe(false);
      expect(result.notebookMode).toBe('paged');
      expect(result.createdAt).toBeTypeOf('number');
      expect(result.updatedAt).toBeTypeOf('number');
      // deletedAt should be absent (not deleted)
      expect(result.deletedAt).toBeUndefined();
    });
  });

  describe('PageSchema', () => {
    it('validates page.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page.fixture.json'), 'utf-8'));
      const result = PageSchema.parse(fixture);
      expect(result.pageId).toBeDefined();
      expect(result.notebookId).toBeDefined();
      expect(result.order).toBeTypeOf('number');
      expect(result.templateType).toBe('blank');
      expect(result.widthPx).toBeTypeOf('number');
      expect(result.heightPx).toBeTypeOf('number');
      expect(result.updatedAt).toBeTypeOf('number');
    });
  });

  describe('StrokeMetaSchema', () => {
    it('validates stroke.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'stroke.fixture.json'), 'utf-8'));
      const result = StrokeMetaSchema.parse(fixture);
      expect(result.strokeId).toBeDefined();
      expect(result.pageId).toBeDefined();
      expect(result.toolType).toBe('pen');
      expect(result.penType).toBe('ballpoint');
      expect(result.colorHex).toBe('#000000');
      expect(result.thickness).toBeTypeOf('number');
      expect(result.opacity).toBeTypeOf('number');
      expect(result.isShape).toBe(false);
      expect(result.createdAt).toBeTypeOf('number');
    });
  });

  describe('PageObjectSchema', () => {
    it('validates page-object-shape.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'page-object-shape.fixture.json'), 'utf-8'));
      const result = PageObjectSchema.parse(fixture);
      expect(result.kind).toBe('shape');
      if (result.kind !== 'shape') throw new Error('Expected shape');
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
  });

  describe('SearchTextSchema', () => {
    it('validates search-text.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'search-text.fixture.json'), 'utf-8'));
      const result = SearchTextSchema.parse(fixture);
      expect(result.source).toBe('handwriting');
      expect(result.notebookId).toBeDefined();
      expect(result.pageId).toBeDefined();
      expect(result.recognizedText).toBeDefined();
      expect(result.extractedAt).toBeTypeOf('number');
    });
  });

  describe('ExportSchema', () => {
    it('validates export.fixture.json', () => {
      const fixture = JSON.parse(readFileSync(join(fixturesDir, 'export.fixture.json'), 'utf-8'));
      const result = ExportSchema.parse(fixture);
      expect(result.mode).toBe('flattened');
      expect(result.notebookId).toBeDefined();
      expect(result.exportAssetId).toBeDefined();
      expect(result.createdByUserId).toBeDefined();
    });
  });
});
