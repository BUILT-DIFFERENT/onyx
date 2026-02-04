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

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"

    fun editor(noteId: String) = "editor/$noteId"
}

@Composable
fun OnyxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToEditor = { noteId ->
                    navController.navigate(Routes.editor(noteId))
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments =
                listOf(
                    navArgument("noteId") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            NoteEditorScreen(
                noteId = noteId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
