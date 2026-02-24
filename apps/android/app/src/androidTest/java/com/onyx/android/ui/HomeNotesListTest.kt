package com.onyx.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.data.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNotesListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notesList_longPressShowsDeleteAction_andInvokesCallback() {
        val note =
            NoteEntity(
                noteId = "note-1",
                ownerUserId = "owner",
                title = "Meeting Notes",
                createdAt = 1L,
                updatedAt = 2L,
                deletedAt = null,
            )
        var requestedDeleteNoteId: String? = null

        composeRule.setContent {
            MaterialTheme {
                NotesListContent(
                    notes = listOf(note),
                    noteTags = emptyMap(),
                    selectionMode = false,
                    selectedNoteIds = emptySet(),
                    onRequestOpenNote = { _, _ -> },
                    resumeLastPageEnabled = true,
                    onRequestDeleteNote = { target -> requestedDeleteNoteId = target.noteId },
                    onMoveNote = {},
                    onManageTags = {},
                    onRequestExportPdf = {},
                    onRequestToggleNoteLock = {},
                    onEnterSelectionMode = {},
                    onToggleNoteSelection = {},
                )
            }
        }

        composeRule.onNodeWithText("Meeting Notes").performTouchInput {
            down(center)
            advanceEventTime(650)
            up()
        }
        composeRule.onNodeWithText("Delete note").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals("note-1", requestedDeleteNoteId)
        }
    }
}
