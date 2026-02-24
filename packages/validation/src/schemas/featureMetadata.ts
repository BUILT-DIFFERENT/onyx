import { z } from 'zod';

export const GestureSettingsSchema = z
  .object({
    profileId: z.string().uuid(),
    ownerUserId: z.string().min(1),
    singleFingerMode: z.enum(['DRAW', 'PAN', 'IGNORE']),
    doubleFingerMode: z.enum(['ZOOM_PAN', 'PAN_ONLY', 'IGNORE']),
    stylusPrimaryAction: z.enum(['ERASER_HOLD', 'ERASER_TOGGLE', 'NO_ACTION']),
    stylusSecondaryAction: z.enum(['ERASER_HOLD', 'ERASER_TOGGLE', 'NO_ACTION']),
    stylusLongHoldAction: z.enum(['ERASER_HOLD', 'ERASER_TOGGLE', 'NO_ACTION']),
    doubleTapZoomAction: z.enum(['NONE', 'CYCLE_PRESET', 'FIT_TO_PAGE']),
    doubleTapZoomPointerMode: z.enum(['FINGER_ONLY', 'FINGER_AND_STYLUS']),
    twoFingerTapAction: z.enum(['NONE', 'UNDO', 'REDO']),
    threeFingerTapAction: z.enum(['NONE', 'UNDO', 'REDO']),
    latencyOptimizationMode: z.enum(['NORMAL', 'FAST_EXPERIMENTAL']),
    updatedAt: z.number().int(),
  })
  .strict();

export const TemplateScopeSchema = z
  .object({
    scopeId: z.string().uuid(),
    noteId: z.string().uuid(),
    templateId: z.string().nullable().optional(),
    backgroundKind: z.enum(['blank', 'grid', 'lined', 'dotted']),
    spacing: z.number().nonnegative(),
    colorHex: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
    paperSize: z.enum(['a4', 'letter', 'infinite']).optional(),
    lineWidth: z.number().positive().optional(),
    category: z.enum(['default', 'planner', 'music', 'custom']).optional(),
    applyScope: z.enum(['currentPage', 'allPages', 'newPages']),
    updatedAt: z.number().int(),
  })
  .strict();

export const ExportMetadataSchema = z
  .object({
    exportId: z.string().uuid(),
    noteId: z.string().uuid(),
    ownerUserId: z.string().min(1),
    format: z.enum(['pdf']),
    mode: z.enum(['flattened', 'layered']),
    status: z.enum(['queued', 'running', 'succeeded', 'failed']),
    assetId: z.string().nullable().optional(),
    requestedAt: z.number().int(),
    completedAt: z.number().int().nullable().optional(),
    errorMessage: z.string().optional(),
  })
  .strict();

export type GestureSettings = z.infer<typeof GestureSettingsSchema>;
export type TemplateScope = z.infer<typeof TemplateScopeSchema>;
export type ExportMetadata = z.infer<typeof ExportMetadataSchema>;
