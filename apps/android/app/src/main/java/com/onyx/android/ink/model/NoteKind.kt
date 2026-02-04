package com.onyx.android.ink.model

enum class NoteKind(
    val value: String,
) {
    INK("ink"), // Standard ink-only page
    PDF("pdf"), // PDF-backed page (no ink)
    MIXED("mixed"), // PDF with ink annotations
    INFINITE("infinite"), // Infinite canvas (Av2)
}
