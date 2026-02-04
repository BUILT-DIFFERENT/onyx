package com.onyx.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun HomeScreen(onNavigateToEditor: (String) -> Unit) {
    val viewModel = remember { HomeScreenViewModel() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val noteId = viewModel.onCreateNote()
                    onNavigateToEditor(noteId)
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Placeholder for notes list (empty until repository wiring in Phase 4).
        }
    }
}

private class HomeScreenViewModel {
    // TODO: Wire to NoteRepository.createNote() after Phase 4 completion.
    fun onCreateNote(): String = UUID.randomUUID().toString()
}
