import { defineSchema, defineTable } from 'convex/server';
import { v } from 'convex/values';

export default defineSchema({
  notes: defineTable({
    noteId: v.string(), // UUID string
    ownerUserId: v.string(), // Clerk user ID
    title: v.string(),
    createdAt: v.number(), // Unix ms timestamp
    updatedAt: v.number(), // Unix ms timestamp
    deletedAt: v.optional(v.number()), // Unix ms timestamp, absent = not deleted
    // Note: folderId is not included because it's local-only metadata.
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_noteId', ['noteId']),
  pageObjects: defineTable({
    objectId: v.string(),
    pageId: v.string(),
    noteId: v.string(),
    kind: v.union(
      v.literal('shape'),
      v.literal('image'),
      v.literal('text'),
      v.literal('audio'),
      v.literal('sticky'),
      v.literal('scan'),
      v.literal('file'),
    ),
    zIndex: v.number(),
    x: v.number(),
    y: v.number(),
    width: v.number(),
    height: v.number(),
    rotationDeg: v.number(),
    payload: v.union(
      v.object({
        shapeType: v.union(v.literal('line'), v.literal('rectangle'), v.literal('ellipse')),
        strokeColor: v.optional(v.string()),
        strokeWidth: v.optional(v.number()),
        fillColor: v.optional(v.union(v.string(), v.null())),
      }),
      v.object({
        assetId: v.optional(v.union(v.string(), v.null())),
        mimeType: v.optional(v.union(v.string(), v.null())),
        sourceUri: v.optional(v.union(v.string(), v.null())),
        displayName: v.optional(v.union(v.string(), v.null())),
      }),
      v.object({
        text: v.optional(v.string()),
        align: v.optional(v.union(v.literal('start'), v.literal('center'), v.literal('end'))),
        color: v.optional(v.string()),
        fontSizeSp: v.optional(v.number()),
        bold: v.optional(v.boolean()),
        italic: v.optional(v.boolean()),
        underline: v.optional(v.boolean()),
      }),
      v.object({
        assetId: v.optional(v.union(v.string(), v.null())),
        mimeType: v.optional(v.union(v.string(), v.null())),
        durationMs: v.optional(v.number()),
        waveformAssetId: v.optional(v.union(v.string(), v.null())),
      }),
      v.object({
        text: v.optional(v.string()),
        color: v.optional(v.string()),
        style: v.optional(v.union(v.literal('square'), v.literal('rounded'))),
      }),
      v.object({
        assetId: v.optional(v.union(v.string(), v.null())),
        pageCount: v.optional(v.number()),
        source: v.optional(v.union(v.literal('camera'), v.literal('gallery'))),
      }),
      v.object({
        assetId: v.optional(v.union(v.string(), v.null())),
        fileName: v.optional(v.string()),
        mimeType: v.optional(v.union(v.string(), v.null())),
        sizeBytes: v.optional(v.number()),
      }),
    ),
    sync: v.optional(
      v.object({
        objectRevision: v.number(),
        parentRevision: v.optional(v.number()),
        lastMutationId: v.string(),
        conflictPolicy: v.union(v.literal('lastWriteWins'), v.literal('manualResolve')),
      }),
    ),
    createdAt: v.number(),
    updatedAt: v.number(),
    deletedAt: v.optional(v.number()),
  })
    .index('by_object_id', ['objectId'])
    .index('by_page', ['pageId'])
    .index('by_note', ['noteId'])
    .index('by_kind', ['kind'])
    .index('by_page_zindex', ['pageId', 'zIndex']),
  searchIndexTokens: defineTable({
    tokenId: v.string(),
    noteId: v.string(),
    pageId: v.string(),
    token: v.string(),
    displayText: v.optional(v.string()),
    source: v.union(v.literal('handwriting'), v.literal('pdfOcr')),
    bounds: v.object({
      x: v.number(),
      y: v.number(),
      width: v.number(),
      height: v.number(),
      rotationDeg: v.optional(v.number()),
    }),
    indexVersion: v.number(),
    mergeKey: v.string(),
    sourceRevision: v.number(),
    sourceUpdatedAt: v.number(),
    indexedAt: v.number(),
    payload: v.union(
      v.object({
        recognitionProvider: v.optional(v.union(v.literal('myscript'), v.literal('mlkit'), v.literal('other'))),
        confidence: v.optional(v.number()),
        strokeIds: v.optional(v.array(v.string())),
      }),
      v.object({
        pdfAssetId: v.string(),
        pdfPageNo: v.number(),
        ocrEngine: v.optional(v.union(v.literal('pdfium'), v.literal('mlkit'), v.literal('tesseract'), v.literal('other'))),
        confidence: v.optional(v.number()),
      }),
    ),
    deletedAt: v.optional(v.number()),
  })
    .index('by_token_id', ['tokenId'])
    .index('by_note', ['noteId'])
    .index('by_page', ['pageId'])
    .index('by_note_source', ['noteId', 'source'])
    .index('by_note_token', ['noteId', 'token']),
  gestureSettings: defineTable({
    profileId: v.string(),
    ownerUserId: v.string(),
    singleFingerMode: v.union(v.literal('DRAW'), v.literal('PAN'), v.literal('IGNORE')),
    doubleFingerMode: v.union(v.literal('ZOOM_PAN'), v.literal('PAN_ONLY'), v.literal('IGNORE')),
    stylusPrimaryAction: v.union(v.literal('ERASER_HOLD'), v.literal('ERASER_TOGGLE'), v.literal('NO_ACTION')),
    stylusSecondaryAction: v.union(v.literal('ERASER_HOLD'), v.literal('ERASER_TOGGLE'), v.literal('NO_ACTION')),
    stylusLongHoldAction: v.union(v.literal('ERASER_HOLD'), v.literal('ERASER_TOGGLE'), v.literal('NO_ACTION')),
    doubleTapZoomAction: v.union(v.literal('NONE'), v.literal('CYCLE_PRESET'), v.literal('FIT_TO_PAGE')),
    doubleTapZoomPointerMode: v.union(v.literal('FINGER_ONLY'), v.literal('FINGER_AND_STYLUS')),
    twoFingerTapAction: v.union(v.literal('NONE'), v.literal('UNDO'), v.literal('REDO')),
    threeFingerTapAction: v.union(v.literal('NONE'), v.literal('UNDO'), v.literal('REDO')),
    latencyOptimizationMode: v.union(v.literal('NORMAL'), v.literal('FAST_EXPERIMENTAL')),
    updatedAt: v.number(),
  })
    .index('by_profile_id', ['profileId'])
    .index('by_owner', ['ownerUserId']),
  templateScopes: defineTable({
    scopeId: v.string(),
    noteId: v.string(),
    templateId: v.optional(v.union(v.string(), v.null())),
    backgroundKind: v.union(v.literal('blank'), v.literal('grid'), v.literal('lined'), v.literal('dotted')),
    spacing: v.number(),
    colorHex: v.string(),
    paperSize: v.optional(v.union(v.literal('a4'), v.literal('letter'), v.literal('infinite'))),
    lineWidth: v.optional(v.number()),
    category: v.optional(v.union(v.literal('default'), v.literal('planner'), v.literal('music'), v.literal('custom'))),
    applyScope: v.union(v.literal('currentPage'), v.literal('allPages'), v.literal('newPages')),
    updatedAt: v.number(),
  })
    .index('by_scope_id', ['scopeId'])
    .index('by_note', ['noteId']),
  exportMetadata: defineTable({
    exportId: v.string(),
    noteId: v.string(),
    ownerUserId: v.string(),
    format: v.literal('pdf'),
    mode: v.union(v.literal('flattened'), v.literal('layered')),
    status: v.union(v.literal('queued'), v.literal('running'), v.literal('succeeded'), v.literal('failed')),
    assetId: v.optional(v.union(v.string(), v.null())),
    requestedAt: v.number(),
    completedAt: v.optional(v.union(v.number(), v.null())),
    errorMessage: v.optional(v.string()),
  })
    .index('by_export_id', ['exportId'])
    .index('by_note', ['noteId'])
    .index('by_owner', ['ownerUserId']),
});
