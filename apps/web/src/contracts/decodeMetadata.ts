import {
  ExportMetadata,
  ExportMetadataSchema,
  GestureSettings,
  GestureSettingsSchema,
  Note,
  NoteSchema,
  PageObject,
  PageObjectSchema,
  SearchIndexToken,
  SearchIndexTokenSchema,
  TemplateScope,
  TemplateScopeSchema,
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
  note: Note;
  pageObjects: PageObject[];
  unknownPageObjects: UnknownPageObject[];
  searchIndexTokens: SearchIndexToken[];
  gestureSettings: GestureSettings[];
  templateScopes: TemplateScope[];
  exportMetadata: ExportMetadata[];
  reports: {
    pageObjects: DecodeReport;
    searchIndexTokens: DecodeReport;
    gestureSettings: DecodeReport;
    templateScopes: DecodeReport;
    exportMetadata: DecodeReport;
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

  const noteParse = NoteSchema.safeParse(root.note);
  if (!noteParse.success) {
    throw new Error('Invalid note payload');
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

  const searchTokensResult = decodeKnownArray(
    asArray(root.searchIndexTokens),
    SearchIndexTokenSchema,
  );
  const gestureSettingsResult = decodeKnownArray(
    asArray(root.gestureSettings),
    GestureSettingsSchema,
  );
  const templateScopesResult = decodeKnownArray(asArray(root.templateScopes), TemplateScopeSchema);
  const exportMetadataResult = decodeKnownArray(asArray(root.exportMetadata), ExportMetadataSchema);

  return {
    note: noteParse.data,
    pageObjects,
    unknownPageObjects,
    searchIndexTokens: searchTokensResult.decoded,
    gestureSettings: gestureSettingsResult.decoded,
    templateScopes: templateScopesResult.decoded,
    exportMetadata: exportMetadataResult.decoded,
    reports: {
      pageObjects: {
        decodedCount: pageObjects.length,
        skippedInvalidCount: invalidPageObjectCount,
      },
      searchIndexTokens: {
        decodedCount: searchTokensResult.decoded.length,
        skippedInvalidCount: searchTokensResult.skippedInvalidCount,
      },
      gestureSettings: {
        decodedCount: gestureSettingsResult.decoded.length,
        skippedInvalidCount: gestureSettingsResult.skippedInvalidCount,
      },
      templateScopes: {
        decodedCount: templateScopesResult.decoded.length,
        skippedInvalidCount: templateScopesResult.skippedInvalidCount,
      },
      exportMetadata: {
        decodedCount: exportMetadataResult.decoded.length,
        skippedInvalidCount: exportMetadataResult.skippedInvalidCount,
      },
    },
  };
}
