@file:Suppress("FunctionName")

package com.onyx.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onyx.android.BuildConfig
import com.onyx.android.ui.DeveloperFlagsScreen
import com.onyx.android.ui.HomeScreen
import com.onyx.android.ui.NoteEditorScreen

@Composable
@Suppress("LongMethod")
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
                onNavigateToSearchResult = { result ->
                    navController.navigate(Routes.editorForSearchResult(result))
                },
                onNavigateToDeveloperFlags = {
                    navController.navigate(Routes.DEVELOPER_FLAGS)
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
                    navArgument("pageIndex") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("highlightLeft") {
                        type = NavType.FloatType
                        defaultValue = Float.NaN
                    },
                    navArgument("highlightTop") {
                        type = NavType.FloatType
                        defaultValue = Float.NaN
                    },
                    navArgument("highlightRight") {
                        type = NavType.FloatType
                        defaultValue = Float.NaN
                    },
                    navArgument("highlightBottom") {
                        type = NavType.FloatType
                        defaultValue = Float.NaN
                    },
                ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            NoteEditorScreen(
                noteId = noteId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        if (BuildConfig.DEBUG) {
            composable(Routes.DEVELOPER_FLAGS) {
                DeveloperFlagsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
