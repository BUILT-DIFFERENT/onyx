import {
  Export,
  ExportSchema,
  Notebook,
  NotebookSchema,
  PageObject,
  PageObjectSchema,
  SearchText,
  SearchTextSchema,
} from '@onyx/validation';
import { z } from 'zod';

const PAGE_OBJECT_KINDS = new Set(['shape', 'image', 'text', 'audio', 'sticky', 'scan', 'file']);

type UnknownRecord = Record<string, unknown>;

export interface UnknownPageObject {
  kind: string;
  raw: UnknownRecord;
}

export interface DecodeReport {
  decodedCount: number;
  skippedInvalidCount: number;
}

export interface DecodedWebMetadata {
  notebook: Notebook;
  pageObjects: PageObject[];
  unknownPageObjects: UnknownPageObject[];
  searchTexts: SearchText[];
  exports: Export[];
  reports: {
    pageObjects: DecodeReport;
    searchTexts: DecodeReport;
    exports: DecodeReport;
  };
}

const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);

const asRecord = (value: unknown): UnknownRecord | null => {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return null;
  }
  return value as UnknownRecord;
};

function decodeKnownArray<T>(
  entries: unknown[],
  schema: z.ZodType<T>,
): { decoded: T[]; skippedInvalidCount: number } {
  const decoded: T[] = [];
  let skippedInvalidCount = 0;

  for (const entry of entries) {
    const parsed = schema.safeParse(entry);
    if (parsed.success) {
      decoded.push(parsed.data);
    } else {
      skippedInvalidCount += 1;
    }
  }

  return { decoded, skippedInvalidCount };
}

export function decodeWebMetadata(payload: unknown): DecodedWebMetadata {
  const root = asRecord(payload);
  if (!root) {
    throw new Error('Metadata payload must be an object');
  }

  const notebookParse = NotebookSchema.safeParse(root.notebook);
  if (!notebookParse.success) {
    throw new Error('Invalid notebook payload');
  }

  const rawPageObjects = asArray(root.pageObjects);
  const pageObjects: PageObject[] = [];
  const unknownPageObjects: UnknownPageObject[] = [];
  let invalidPageObjectCount = 0;

  for (const entry of rawPageObjects) {
    const candidate = asRecord(entry);
    if (!candidate) {
      invalidPageObjectCount += 1;
      continue;
    }

    const kind = candidate.kind;
    if (typeof kind !== 'string') {
      invalidPageObjectCount += 1;
      continue;
    }

    if (!PAGE_OBJECT_KINDS.has(kind)) {
      unknownPageObjects.push({ kind, raw: candidate });
      continue;
    }

    const parsed = PageObjectSchema.safeParse(candidate);
    if (!parsed.success) {
      invalidPageObjectCount += 1;
      continue;
    }
    pageObjects.push(parsed.data);
  }

  const searchTextsResult = decodeKnownArray(asArray(root.searchTexts), SearchTextSchema);
  const exportsResult = decodeKnownArray(asArray(root.exports), ExportSchema);

  return {
    notebook: notebookParse.data,
    pageObjects,
    unknownPageObjects,
    searchTexts: searchTextsResult.decoded,
    exports: exportsResult.decoded,
    reports: {
      pageObjects: {
        decodedCount: pageObjects.length,
        skippedInvalidCount: invalidPageObjectCount,
      },
      searchTexts: {
        decodedCount: searchTextsResult.decoded.length,
        skippedInvalidCount: searchTextsResult.skippedInvalidCount,
      },
      exports: {
        decodedCount: exportsResult.decoded.length,
        skippedInvalidCount: exportsResult.skippedInvalidCount,
      },
    },
  };
}
