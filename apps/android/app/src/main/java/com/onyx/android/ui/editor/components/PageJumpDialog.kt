@file:Suppress("FunctionName", "MagicNumber")

package com.onyx.android.ui.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var pageInput by rememberSaveable { mutableStateOf(currentPage.toString()) }
    val isValidPage = pageInput.toIntOrNull()?.let { it in 1..totalPages } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column {
                Text("Enter page number (1-$totalPages)")
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions =
                        KeyboardActions(onGo = {
                            val pageNumber = pageInput.toIntOrNull()
                            if (pageNumber != null && pageNumber in 1..totalPages) {
                                onJump(pageNumber)
                            }
                        }),
                    isError = pageInput.isNotEmpty() && !isValidPage,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pageNumber = pageInput.toIntOrNull()
                    if (pageNumber != null && pageNumber in 1..totalPages) {
                        onJump(pageNumber)
                    }
                },
                enabled = isValidPage,
            ) {
                Text("Go")
            }
        },
    )
}
