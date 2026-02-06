@file:Suppress("FunctionName")

package com.onyx.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onyx.android.ui.HomeScreen
import com.onyx.android.ui.NoteEditorScreen

@Composable
fun OnyxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEditor = { noteId, pageId ->
                    navController.navigate(Routes.editor(noteId, pageId))
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments =
                listOf(
                    navArgument("noteId") { type = NavType.StringType },
                    navArgument("pageId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            val pageId = backStackEntry.arguments?.getString("pageId")
            NoteEditorScreen(
                noteId = noteId,
                initialPageId = pageId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
