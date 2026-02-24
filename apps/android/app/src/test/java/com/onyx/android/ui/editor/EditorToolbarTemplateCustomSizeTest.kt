package com.onyx.android.ui.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EditorToolbarTemplateCustomSizeTest {
    @Test
    fun `buildCustomPaperTemplateId returns null for invalid numeric input`() {
        val invalid = buildCustomPaperTemplateId(widthInput = "abc", heightInput = "500", unit = "pt")
        assertNull(invalid)
    }

    @Test
    fun `buildCustomPaperTemplateId normalizes in to point storage`() {
        val templateId = buildCustomPaperTemplateId(widthInput = "8.5", heightInput = "11", unit = "in")
        assertNotNull(templateId)
        val parsed = parseCustomPaperTemplate(templateId)
        assertNotNull(parsed)
        assertEquals(612f, parsed!!.widthPt)
        assertEquals(792f, parsed.heightPt)
        assertEquals("in", parsed.unit)
    }

    @Test
    fun `parseCustomPaperTemplate returns null for non-custom template`() {
        assertNull(parseCustomPaperTemplate("paper:letter"))
    }
}
