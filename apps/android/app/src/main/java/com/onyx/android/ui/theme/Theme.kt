@file:Suppress("FunctionName")

package com.onyx.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
    lightColorScheme(
        primary = OnyxPrimary,
        secondary = OnyxSecondary,
        tertiary = OnyxTertiary,
        surface = OnyxSurface,
        onSurface = OnyxOnSurface,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = OnyxTertiary,
        secondary = OnyxSecondary,
        tertiary = OnyxPrimary,
        surface = OnyxSurfaceDark,
        onSurface = OnyxOnSurfaceDark,
    )

@Composable
fun OnyxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
