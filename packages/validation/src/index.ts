// ─── Core entity schemas ─────────────────────────────────────────────────────
export { NotebookSchema, type Notebook } from './schemas/notebook';
export { FolderSchema, type Folder } from './schemas/folder';
export { PageSchema, type Page } from './schemas/page';
export { StrokeMetaSchema, type StrokeMeta } from './schemas/stroke';
export { PageObjectSchema, type PageObject } from './schemas/pageObject';

// ─── Sync / collaboration schemas ────────────────────────────────────────────
export { CrdtUpdateSchema, type CrdtUpdate } from './schemas/crdtUpdate';
export { ShareSchema, type Share } from './schemas/share';
export { PublicLinkSchema, type PublicLink } from './schemas/publicLink';
export { PresenceSchema, type Presence } from './schemas/presence';

// ─── Content / asset schemas ─────────────────────────────────────────────────
export { AssetSchema, type Asset } from './schemas/asset';
export { SearchTextSchema, type SearchText } from './schemas/searchText';
export { ExportSchema, type Export } from './schemas/export';

// ─── Supporting schemas ──────────────────────────────────────────────────────
export { BoundsSchema, type Bounds } from './schemas/common';
