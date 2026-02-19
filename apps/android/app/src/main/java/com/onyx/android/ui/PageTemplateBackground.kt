@file:Suppress("FunctionName", "MagicNumber", "LongParameterList", "MatchingDeclarationName")

package com.onyx.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.onyx.android.ink.model.ViewTransform
import java.util.Locale

internal data class PageTemplateState(
    val templateId: String?,
    val backgroundKind: String,
    val spacing: Float,
    val color: Color,
) {
    companion object {
        val BLANK =
            PageTemplateState(
                templateId = null,
                backgroundKind = "blank",
                spacing = 0f,
                color = Color.Transparent,
            )

        val DEFAULT_GRID =
            PageTemplateState(
                templateId = "builtin-grid",
                backgroundKind = "grid",
                spacing = 24f,
                color = Color(0xFFE0E0E0),
            )

        val DEFAULT_LINED =
            PageTemplateState(
                templateId = "builtin-lined",
                backgroundKind = "lined",
                spacing = 24f,
                color = Color(0xFFE0E0E0),
            )

        val DEFAULT_DOTTED =
            PageTemplateState(
                templateId = "builtin-dotted",
                backgroundKind = "dotted",
                spacing = 24f,
                color = Color(0xFFE0E0E0),
            )
    }
}

internal data class TemplateLine(
    val start: Offset,
    val end: Offset,
)

internal data class TemplateDot(
    val center: Offset,
)

internal data class TemplatePattern(
    val lines: List<TemplateLine> = emptyList(),
    val dots: List<TemplateDot> = emptyList(),
)

@Suppress("SwallowedException")
internal fun parseTemplateColor(colorHex: String?): Color {
    if (colorHex.isNullOrBlank()) return Color(0xFFE0E0E0)
    return try {
        val parsed = android.graphics.Color.parseColor(colorHex)
        Color(parsed)
    } catch (e: IllegalArgumentException) {
        Color(0xFFE0E0E0)
    }
}

internal fun templateColorToHex(color: Color): String {
    val red = (color.red * 255).toInt().coerceIn(0, 255)
    val green = (color.green * 255).toInt().coerceIn(0, 255)
    val blue = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
}

internal fun spacingRangeForTemplate(backgroundKind: String): ClosedFloatingPointRange<Float> =
    when (backgroundKind) {
        "grid" -> 20f..60f
        "lined" -> 30f..50f
        "dotted" -> 10f..20f
        else -> 12f..48f
    }

internal fun computeTemplatePattern(
    backgroundKind: String,
    pageWidth: Float,
    pageHeight: Float,
    spacing: Float,
): TemplatePattern {
    if (pageWidth <= 0f || pageHeight <= 0f || spacing <= 0f) {
        return TemplatePattern()
    }
    return when (backgroundKind) {
        "grid" -> {
            val lines = mutableListOf<TemplateLine>()
            var x = 0f
            while (x <= pageWidth) {
                lines += TemplateLine(start = Offset(x, 0f), end = Offset(x, pageHeight))
                x += spacing
            }
            var y = 0f
            while (y <= pageHeight) {
                lines += TemplateLine(start = Offset(0f, y), end = Offset(pageWidth, y))
                y += spacing
            }
            TemplatePattern(lines = lines)
        }

        "lined" -> {
            val lines = mutableListOf<TemplateLine>()
            var y = spacing
            while (y <= pageHeight) {
                lines += TemplateLine(start = Offset(0f, y), end = Offset(pageWidth, y))
                y += spacing
            }
            TemplatePattern(lines = lines)
        }

        "dotted" -> {
            val dots = mutableListOf<TemplateDot>()
            var y = spacing / 2f
            while (y <= pageHeight) {
                var x = spacing / 2f
                while (x <= pageWidth) {
                    dots += TemplateDot(center = Offset(x, y))
                    x += spacing
                }
                y += spacing
            }
            TemplatePattern(dots = dots)
        }

        else -> {
            TemplatePattern()
        }
    }
}

@Composable
internal fun PageTemplateBackground(
    templateState: PageTemplateState,
    pageWidth: Float,
    pageHeight: Float,
    viewTransform: ViewTransform,
    modifier: Modifier = Modifier,
) {
    if (templateState.backgroundKind == "blank" || pageWidth <= 0f || pageHeight <= 0f) {
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val pattern =
            computeTemplatePattern(
                backgroundKind = templateState.backgroundKind,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                spacing = templateState.spacing,
            )
        if (pattern.lines.isEmpty() && pattern.dots.isEmpty()) {
            return@Canvas
        }
        val left = viewTransform.pageToScreenX(0f)
        val top = viewTransform.pageToScreenY(0f)
        val zoom = viewTransform.zoom.coerceAtLeast(0.0001f)
        val strokeWidth = 1f / zoom
        val dotRadius = maxOf((templateState.spacing * 0.08f), 0.9f / zoom)

        withTransform({
            translate(left = left, top = top)
            scale(scaleX = zoom, scaleY = zoom)
        }) {
            pattern.lines.forEach { line ->
                drawLine(
                    color = templateState.color,
                    start = line.start,
                    end = line.end,
                    strokeWidth = strokeWidth,
                )
            }
            pattern.dots.forEach { dot ->
                drawCircle(
                    color = templateState.color,
                    radius = dotRadius,
                    center = dot.center,
                )
            }
        }
    }
}
