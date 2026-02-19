@file:Suppress("MagicNumber")

package com.onyx.android.ink.model

import kotlin.math.floor

private const val DEFAULT_CELL_SIZE = 200f

class SpatialIndex(
    private val cellSize: Float = DEFAULT_CELL_SIZE,
) {
    private val cells: MutableMap<Long, MutableSet<String>> = mutableMapOf()
    private val strokeBounds: MutableMap<String, StrokeBounds> = mutableMapOf()

    fun insert(stroke: Stroke) {
        strokeBounds[stroke.id] = stroke.bounds
        val cellKeys = boundsToCellKeys(stroke.bounds)
        for (key in cellKeys) {
            cells.getOrPut(key) { mutableSetOf() }.add(stroke.id)
        }
    }

    fun remove(strokeId: String) {
        val bounds = strokeBounds.remove(strokeId) ?: return
        val cellKeys = boundsToCellKeys(bounds)
        for (key in cellKeys) {
            cells[key]?.remove(strokeId)
            if (cells[key]?.isEmpty() == true) {
                cells.remove(key)
            }
        }
    }

    fun clear() {
        cells.clear()
        strokeBounds.clear()
    }

    fun query(bounds: StrokeBounds): Set<String> {
        val cellKeys = boundsToCellKeys(bounds)
        val result = mutableSetOf<String>()
        for (key in cellKeys) {
            cells[key]?.let { result.addAll(it) }
        }
        return result
    }

    fun queryPolygon(polygon: List<Pair<Float, Float>>): Set<String> {
        if (polygon.isEmpty()) return emptySet()
        val polygonBounds = calculatePolygonBounds(polygon)
        val candidateIds = query(polygonBounds)
        return candidateIds
    }

    private fun boundsToCellKeys(bounds: StrokeBounds): Set<Long> {
        val keys = mutableSetOf<Long>()
        val minCellX = floor(bounds.x / cellSize).toInt()
        val minCellY = floor(bounds.y / cellSize).toInt()
        val maxCellX = floor((bounds.x + bounds.w) / cellSize).toInt()
        val maxCellY = floor((bounds.y + bounds.h) / cellSize).toInt()
        for (cx in minCellX..maxCellX) {
            for (cy in minCellY..maxCellY) {
                keys.add(cellKey(cx, cy))
            }
        }
        return keys
    }

    private fun cellKey(
        cx: Int,
        cy: Int,
    ): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private fun calculatePolygonBounds(polygon: List<Pair<Float, Float>>): StrokeBounds {
        if (polygon.isEmpty()) return StrokeBounds(0f, 0f, 0f, 0f)
        var minX = polygon[0].first
        var minY = polygon[0].second
        var maxX = polygon[0].first
        var maxY = polygon[0].second
        for (point in polygon) {
            minX = minOf(minX, point.first)
            minY = minOf(minY, point.second)
            maxX = maxOf(maxX, point.first)
            maxY = maxOf(maxY, point.second)
        }
        return StrokeBounds(x = minX, y = minY, w = maxX - minX, h = maxY - minY)
    }
}
