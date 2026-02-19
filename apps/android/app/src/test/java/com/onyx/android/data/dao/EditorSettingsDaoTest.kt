package com.onyx.android.data.dao

import com.onyx.android.data.entity.EditorSettingsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EditorSettingsDaoTest {
    @Test
    fun `saveSettings keeps a singleton row`() =
        runTest {
            val dao = FakeEditorSettingsDao()

            dao.saveSettings(EditorSettingsEntity(settingsId = "default", penColor = "#000000"))
            dao.saveSettings(EditorSettingsEntity(settingsId = "default", penColor = "#123456"))

            val saved = dao.getSettingsOnce()
            assertNotNull(saved)
            assertEquals("default", saved?.settingsId)
            assertEquals("#123456", saved?.penColor)
        }

    @Test
    fun `saveSettings replaces existing values`() =
        runTest {
            val dao = FakeEditorSettingsDao()

            dao.saveSettings(
                EditorSettingsEntity(settingsId = "default", selectedTool = "PEN", penBaseWidth = 2f),
            )
            dao.saveSettings(
                EditorSettingsEntity(settingsId = "default", selectedTool = "HIGHLIGHTER", penBaseWidth = 5f),
            )

            val saved = dao.getSettingsOnce()
            assertEquals("HIGHLIGHTER", saved?.selectedTool)
            assertEquals(5f, saved?.penBaseWidth)
        }

    @Test
    fun `getSettings flow emits updates`() =
        runTest {
            val dao = FakeEditorSettingsDao()
            dao.saveSettings(EditorSettingsEntity(settingsId = "default", selectedTool = "PEN"))

            val first = dao.getSettings().first()
            assertEquals("PEN", first?.selectedTool)

            dao.saveSettings(EditorSettingsEntity(settingsId = "default", selectedTool = "ERASER"))
            val second = dao.getSettings().first()
            assertEquals("ERASER", second?.selectedTool)
        }

    private class FakeEditorSettingsDao : EditorSettingsDao {
        private val state = MutableStateFlow<EditorSettingsEntity?>(null)

        override fun getSettings() = state

        override suspend fun getSettingsOnce(): EditorSettingsEntity? = state.value

        override suspend fun saveSettings(settings: EditorSettingsEntity) {
            state.value = settings
        }
    }
}
