@file:Suppress(
    "TooManyFunctions",
    "LargeClass",
    "MagicNumber",
    "ReturnCount",
    "MaxLineLength",
    "ComplexCondition",
)

package com.onyx.android.ink.gl

import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Trace
import android.util.Log
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.ColorCache
import com.onyx.android.ink.ui.HIGHLIGHTER_STROKE_ALPHA
import com.onyx.android.ink.ui.catmullRomSmooth
import com.onyx.android.ink.ui.computePerPointWidths
import com.onyx.android.ink.ui.computeStrokeOutlineGeometry
import com.onyx.android.ink.ui.resolveStyledSampleRanges
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal class InkGlRenderer : GLSurfaceView.Renderer {
    private data class ProgramHandles(
        val position: Int,
        val zoom: Int,
        val pan: Int,
        val viewport: Int,
        val clipRect: Int,
        val color: Int,
    )

    private data class MeshRecord(
        val vboId: Int,
        val vertexCount: Int,
        val byteSize: Int,
        val fingerprint: Int,
        val bounds: StrokeBounds,
    )

    private data class CommittedStrokeRecord(
        val id: String,
        val style: StrokeStyle,
        val argbColor: Int,
        val alphaMultiplier: Float,
        val mesh: MeshRecord,
    )

    private data class ActiveStrokeState(
        val brush: GlBrush,
        val points: MutableList<StrokePoint>,
        var mesh: MeshRecord? = null,
        var dirty: Boolean = true,
    )

    private data class StyleBucketKey(
        val argbColor: Int,
        val alphaMultiplier: Float,
    )

    private data class FrameStatsSnapshot(
        val p50Ms: Double,
        val p95Ms: Double,
        val worstJankBurstMs: Double,
        val transformToFrameMs: Double,
    )

    private val eglController = EglController()
    private var shaderProgram: ShaderProgram? = null
    private var handles: ProgramHandles? = null

    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    @Volatile
    private var viewTransform: ViewTransform = ViewTransform.DEFAULT

    @Volatile
    private var overlayState: GlOverlayState = GlOverlayState()

    @Volatile
    private var latestTransformUpdateNanos: Long = 0L

    private var pageWidth: Float = 0f
    private var pageHeight: Float = 0f

    private val committedStrokes = LinkedHashMap<String, CommittedStrokeRecord>()
    private val activeStrokes = LinkedHashMap<Long, ActiveStrokeState>()
    private val finishedStrokes = LinkedHashMap<Long, ActiveStrokeState>()

    private val spatialIndex = HashMap<Long, MutableSet<String>>()

    private var overlayLassoVboId = 0
    private var overlayHoverVboId = 0

    private val frameDurationsNs = LongArray(180)
    private var frameDurationIndex = 0
    private var frameDurationCount = 0
    private var currentJankBurstNs = 0L
    private var worstJankBurstNs = 0L
    private var lastMetricsLogNs = 0L

    private val vertexShaderSource =
        """
        attribute vec2 aPosition;
        uniform float uZoom;
        uniform vec2 uPan;
        uniform vec2 uViewport;
        varying vec2 vScreenPos;

        void main() {
            vec2 screenPos = (aPosition * uZoom) + uPan;
            vScreenPos = screenPos;
            vec2 ndc;
            ndc.x = (screenPos.x / uViewport.x) * 2.0 - 1.0;
            ndc.y = 1.0 - (screenPos.y / uViewport.y) * 2.0;
            gl_Position = vec4(ndc, 0.0, 1.0);
        }
        """.trimIndent()

    private val fragmentShaderSource =
        """
        precision mediump float;
        uniform vec4 uColor;
        uniform vec4 uClipRect;
        varying vec2 vScreenPos;

        void main() {
            if (
                vScreenPos.x < uClipRect.x ||
                vScreenPos.x > uClipRect.z ||
                vScreenPos.y < uClipRect.y ||
                vScreenPos.y > uClipRect.w
            ) {
                discard;
            }
            gl_FragColor = uColor;
        }
        """.trimIndent()

    fun setViewTransform(
        transform: ViewTransform,
        updateNanos: Long,
    ) {
        viewTransform = transform
        latestTransformUpdateNanos = updateNanos
    }

    fun setOverlayState(overlay: GlOverlayState) {
        overlayState = overlay
    }

    fun setCommittedStrokes(
        strokes: List<Stroke>,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        this.pageWidth = pageWidth
        this.pageHeight = pageHeight

        val incomingIds = strokes.asSequence().map { it.id }.toHashSet()
        val staleIds = committedStrokes.keys.filter { it !in incomingIds }
        staleIds.forEach { staleId ->
            deleteMesh(committedStrokes.remove(staleId)?.mesh)
        }

        strokes.forEach { stroke ->
            val style = stroke.style
            val argbColor = ColorCache.resolve(style.color)
            val alphaMultiplier = if (style.tool == Tool.HIGHLIGHTER) HIGHLIGHTER_STROKE_ALPHA else 1f
            val fingerprint = stroke.points.hashCode() * 31 + style.hashCode()
            val existing = committedStrokes[stroke.id]

            val updatedMesh =
                if (existing == null || existing.mesh.fingerprint != fingerprint) {
                    val vertices = buildStrokeMesh(stroke.points, style)
                    uploadMesh(
                        vertices = vertices,
                        fingerprint = fingerprint,
                        bounds = stroke.bounds,
                        existing = existing?.mesh,
                        usage = GLES20.GL_STATIC_DRAW,
                    )
                } else {
                    existing.mesh
                }

            committedStrokes[stroke.id] =
                CommittedStrokeRecord(
                    id = stroke.id,
                    style = style,
                    argbColor = argbColor,
                    alphaMultiplier = alphaMultiplier,
                    mesh = updatedMesh,
                )
        }

        rebuildSpatialIndex()
        evictCommittedMeshBudget()
    }

    fun startStroke(
        strokeId: Long,
        input: GlStrokeInput,
        brush: GlBrush,
    ) {
        activeStrokes[strokeId] =
            ActiveStrokeState(
                brush = brush,
                points = mutableListOf(input.toStrokePoint()),
            )
    }

    fun addToStroke(
        strokeId: Long,
        input: GlStrokeInput,
    ) {
        val strokeState = activeStrokes[strokeId] ?: return
        val point = input.toStrokePoint()
        val last = strokeState.points.lastOrNull()
        if (last == null || last.x != point.x || last.y != point.y || last.p != point.p) {
            strokeState.points.add(point)
            strokeState.dirty = true
        }
    }

    fun finishStroke(
        strokeId: Long,
        input: GlStrokeInput,
    ) {
        addToStroke(strokeId, input)
        val strokeState = activeStrokes.remove(strokeId) ?: return
        finishedStrokes[strokeId] = strokeState
    }

    fun cancelStroke(strokeId: Long) {
        deleteMesh(activeStrokes.remove(strokeId)?.mesh)
        deleteMesh(finishedStrokes.remove(strokeId)?.mesh)
    }

    fun removeFinishedStrokes(strokeIds: Set<Long>) {
        strokeIds.forEach { strokeId ->
            deleteMesh(finishedStrokes.remove(strokeId)?.mesh)
        }
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        eglController.markGlThread()
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val program = ShaderProgram(vertexShaderSource, fragmentShaderSource)
        shaderProgram = program
        handles =
            ProgramHandles(
                position = GLES20.glGetAttribLocation(program.programId, "aPosition"),
                zoom = GLES20.glGetUniformLocation(program.programId, "uZoom"),
                pan = GLES20.glGetUniformLocation(program.programId, "uPan"),
                viewport = GLES20.glGetUniformLocation(program.programId, "uViewport"),
                clipRect = GLES20.glGetUniformLocation(program.programId, "uClipRect"),
                color = GLES20.glGetUniformLocation(program.programId, "uColor"),
            )

        overlayLassoVboId = createBufferId()
        overlayHoverVboId = createBufferId()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int,
    ) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStartNs = System.nanoTime()
        Trace.beginSection("InkGlRenderer#onDrawFrame")
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val program = shaderProgram ?: return
            val handles = handles ?: return
            GLES20.glUseProgram(program.programId)

            applyFrameUniforms(handles)

            val visibleStrokeIds = computeVisibleStrokeIds()
            drawCommittedStrokes(handles, visibleStrokeIds)
            drawActiveStrokes(handles)
            drawSelectedStrokes(handles, visibleStrokeIds)
            drawLassoOverlay(handles)
            drawHoverOverlay(handles)
        } finally {
            Trace.endSection()
            recordFrameStats(frameStartNs)
        }
    }

    fun release() {
        ShaderProgram.safeRelease(shaderProgram)
        shaderProgram = null
        handles = null

        committedStrokes.values.forEach { deleteMesh(it.mesh) }
        activeStrokes.values.forEach { deleteMesh(it.mesh) }
        finishedStrokes.values.forEach { deleteMesh(it.mesh) }

        committedStrokes.clear()
        activeStrokes.clear()
        finishedStrokes.clear()
        spatialIndex.clear()

        deleteBuffer(overlayLassoVboId)
        deleteBuffer(overlayHoverVboId)
        overlayLassoVboId = 0
        overlayHoverVboId = 0
    }

    private fun drawCommittedStrokes(
        handles: ProgramHandles,
        visibleStrokeIds: Set<String>,
    ) {
        if (visibleStrokeIds.isEmpty()) {
            return
        }
        val buckets = LinkedHashMap<StyleBucketKey, MutableList<MeshRecord>>()
        visibleStrokeIds.forEach { strokeId ->
            val record = committedStrokes[strokeId] ?: return@forEach
            if (record.mesh.vertexCount <= 0) {
                return@forEach
            }
            val key = StyleBucketKey(record.argbColor, record.alphaMultiplier)
            buckets.getOrPut(key) { mutableListOf() }.add(record.mesh)
        }

        buckets.forEach { (key, meshes) ->
            val color = argbToRgba(key.argbColor, key.alphaMultiplier)
            GLES20.glUniform4f(handles.color, color[0], color[1], color[2], color[3])
            meshes.forEach { mesh ->
                drawMeshTriangles(handles, mesh)
            }
        }
    }

    private fun drawActiveStrokes(handles: ProgramHandles) {
        activeStrokes.values.forEach { stroke ->
            stroke.mesh = ensureActiveMesh(stroke)
            val mesh = stroke.mesh ?: return@forEach
            if (mesh.vertexCount <= 0) return@forEach
            val color = argbToRgba(stroke.brush.argbColor, stroke.brush.alphaMultiplier)
            GLES20.glUniform4f(handles.color, color[0], color[1], color[2], color[3])
            drawMeshTriangles(handles, mesh)
        }

        finishedStrokes.values.forEach { stroke ->
            stroke.mesh = ensureActiveMesh(stroke)
            val mesh = stroke.mesh ?: return@forEach
            if (mesh.vertexCount <= 0) return@forEach
            val color = argbToRgba(stroke.brush.argbColor, stroke.brush.alphaMultiplier)
            GLES20.glUniform4f(handles.color, color[0], color[1], color[2], color[3])
            drawMeshTriangles(handles, mesh)
        }
    }

    private fun drawSelectedStrokes(
        handles: ProgramHandles,
        visibleStrokeIds: Set<String>,
    ) {
        val selectedIds = overlayState.selectedStrokeIds
        if (selectedIds.isEmpty()) {
            return
        }
        val highlightColor = argbToRgba(SELECTION_HIGHLIGHT_COLOR, SELECTION_HIGHLIGHT_ALPHA)
        GLES20.glUniform4f(handles.color, highlightColor[0], highlightColor[1], highlightColor[2], highlightColor[3])
        selectedIds
            .asSequence()
            .filter { it in visibleStrokeIds }
            .forEach { selectedId ->
                val mesh = committedStrokes[selectedId]?.mesh ?: return@forEach
                if (mesh.vertexCount > 0) {
                    drawMeshTriangles(handles, mesh)
                }
            }
    }

    private fun drawLassoOverlay(handles: ProgramHandles) {
        val lassoPath = overlayState.lassoPath
        if (lassoPath.size < 2 || overlayLassoVboId == 0) {
            return
        }

        val vertices = FloatArray(lassoPath.size * 2)
        var cursor = 0
        lassoPath.forEach { point ->
            vertices[cursor++] = point.first
            vertices[cursor++] = point.second
        }

        uploadLineVertices(overlayLassoVboId, vertices)
        val color = argbToRgba(LASSO_COLOR, LASSO_ALPHA)
        GLES20.glUniform4f(handles.color, color[0], color[1], color[2], color[3])
        drawLineStrip(handles, overlayLassoVboId, vertices.size / 2, LASSO_LINE_WIDTH)
    }

    private fun drawHoverOverlay(handles: ProgramHandles) {
        val hover = overlayState.hover
        if (!hover.isVisible || hover.screenRadius <= 0f || overlayHoverVboId == 0) {
            return
        }

        val transform = viewTransform
        val centerX = transform.screenToPageX(hover.screenX)
        val centerY = transform.screenToPageY(hover.screenY)
        val radius = (hover.screenRadius / transform.zoom.coerceAtLeast(MIN_ZOOM_EPSILON)).coerceAtLeast(0.1f)

        val segments = HOVER_CIRCLE_SEGMENTS
        val vertices = FloatArray((segments + 1) * 2)
        for (segment in 0..segments) {
            val angle = (segment.toFloat() / segments.toFloat()) * TWO_PI
            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius
            val base = segment * 2
            vertices[base] = x
            vertices[base + 1] = y
        }

        uploadLineVertices(overlayHoverVboId, vertices)
        val color = argbToRgba(hover.argbColor, hover.alpha)
        GLES20.glUniform4f(handles.color, color[0], color[1], color[2], color[3])
        drawLineStrip(handles, overlayHoverVboId, vertices.size / 2, HOVER_LINE_WIDTH)
    }

    private fun drawMeshTriangles(
        handles: ProgramHandles,
        mesh: MeshRecord,
    ) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboId)
        GLES20.glEnableVertexAttribArray(handles.position)
        GLES20.glVertexAttribPointer(handles.position, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
        GLES20.glDisableVertexAttribArray(handles.position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawLineStrip(
        handles: ProgramHandles,
        vboId: Int,
        vertexCount: Int,
        lineWidth: Float,
    ) {
        if (vertexCount <= 1) {
            return
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(handles.position)
        GLES20.glVertexAttribPointer(handles.position, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glLineWidth(lineWidth)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(handles.position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun applyFrameUniforms(handles: ProgramHandles) {
        val transform = viewTransform
        GLES20.glUniform1f(handles.zoom, transform.zoom)
        GLES20.glUniform2f(handles.pan, transform.panX, transform.panY)
        GLES20.glUniform2f(handles.viewport, viewportWidth.toFloat(), viewportHeight.toFloat())

        val left = transform.pageToScreenX(0f)
        val top = transform.pageToScreenY(0f)
        val right = left + transform.pageWidthToScreen(pageWidth)
        val bottom = top + transform.pageWidthToScreen(pageHeight)
        GLES20.glUniform4f(handles.clipRect, left, top, right, bottom)
    }

    private fun ensureActiveMesh(stroke: ActiveStrokeState): MeshRecord? {
        val existing = stroke.mesh
        if (!stroke.dirty && existing != null) {
            return existing
        }

        val bounds = computeBounds(stroke.points)
        val fingerprint = stroke.points.hashCode() * 31 + stroke.brush.strokeStyle.hashCode()
        val vertices = buildStrokeMesh(stroke.points, stroke.brush.strokeStyle)
        val updated =
            uploadMesh(
                vertices = vertices,
                fingerprint = fingerprint,
                bounds = bounds,
                existing = existing,
                usage = GLES20.GL_DYNAMIC_DRAW,
            )
        stroke.dirty = false
        return updated
    }

    private fun computeVisibleStrokeIds(): Set<String> {
        if (committedStrokes.isEmpty()) {
            return emptySet()
        }

        val viewportPageRect = computeViewportPageRect() ?: return committedStrokes.keys
        val margin = CULL_MARGIN_SCREEN_PX / viewTransform.zoom.coerceAtLeast(MIN_ZOOM_EPSILON)
        viewportPageRect.inset(-margin, -margin)

        val minCellX = floor(viewportPageRect.left / SPATIAL_CELL_SIZE).toInt()
        val maxCellX = ceil(viewportPageRect.right / SPATIAL_CELL_SIZE).toInt()
        val minCellY = floor(viewportPageRect.top / SPATIAL_CELL_SIZE).toInt()
        val maxCellY = ceil(viewportPageRect.bottom / SPATIAL_CELL_SIZE).toInt()

        val candidateIds = LinkedHashSet<String>()
        for (x in minCellX..maxCellX) {
            for (y in minCellY..maxCellY) {
                spatialIndex[packCellKey(x, y)]?.let { bucket ->
                    candidateIds.addAll(bucket)
                }
            }
        }

        if (candidateIds.isEmpty()) {
            return emptySet()
        }

        return candidateIds.filterTo(LinkedHashSet()) { strokeId ->
            val bounds = committedStrokes[strokeId]?.mesh?.bounds ?: return@filterTo false
            bounds.overlaps(viewportPageRect)
        }
    }

    private fun computeViewportPageRect(): RectF? {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return null
        }

        val transform = viewTransform
        val left = transform.screenToPageX(0f)
        val top = transform.screenToPageY(0f)
        val right = transform.screenToPageX(viewportWidth.toFloat())
        val bottom = transform.screenToPageY(viewportHeight.toFloat())

        return RectF(
            minOf(left, right),
            minOf(top, bottom),
            maxOf(left, right),
            maxOf(top, bottom),
        )
    }

    private fun rebuildSpatialIndex() {
        spatialIndex.clear()
        committedStrokes.values.forEach { record ->
            val bounds = record.mesh.bounds
            val minCellX = floor(bounds.x / SPATIAL_CELL_SIZE).toInt()
            val maxCellX = floor((bounds.x + bounds.w) / SPATIAL_CELL_SIZE).toInt()
            val minCellY = floor(bounds.y / SPATIAL_CELL_SIZE).toInt()
            val maxCellY = floor((bounds.y + bounds.h) / SPATIAL_CELL_SIZE).toInt()
            for (x in minCellX..maxCellX) {
                for (y in minCellY..maxCellY) {
                    spatialIndex
                        .getOrPut(packCellKey(x, y)) { LinkedHashSet() }
                        .add(record.id)
                }
            }
        }
    }

    private fun uploadMesh(
        vertices: FloatArray,
        fingerprint: Int,
        bounds: StrokeBounds,
        existing: MeshRecord?,
        usage: Int,
    ): MeshRecord {
        val vboId = existing?.vboId ?: createBufferId()
        val vertexCount = vertices.size / 2
        val byteSize = vertices.size * FLOAT_SIZE_BYTES

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        if (byteSize > 0) {
            val buffer = vertices.toFloatBuffer()
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, byteSize, buffer, usage)
        } else {
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 0, null, usage)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        return MeshRecord(
            vboId = vboId,
            vertexCount = vertexCount,
            byteSize = byteSize,
            fingerprint = fingerprint,
            bounds = bounds,
        )
    }

    private fun uploadLineVertices(
        vboId: Int,
        vertices: FloatArray,
    ) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        val buffer = vertices.toFloatBuffer()
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertices.size * FLOAT_SIZE_BYTES,
            buffer,
            GLES20.GL_DYNAMIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun buildStrokeMesh(
        points: List<StrokePoint>,
        style: StrokeStyle,
    ): FloatArray {
        if (points.isEmpty()) {
            return FloatArray(0)
        }
        val samples = catmullRomSmooth(points, style.smoothingLevel)
        if (samples.isEmpty()) {
            return FloatArray(0)
        }
        val widths = computePerPointWidths(samples, style)
        val ranges = resolveStyledSampleRanges(samples, style)
        val vertexList = ArrayList<Float>(samples.size * 12)
        ranges.forEach { range ->
            if (range.first > range.last || range.last >= samples.size) {
                return@forEach
            }
            appendMeshVerticesForRange(
                samples = samples,
                widths = widths,
                style = style,
                range = range,
                outVertices = vertexList,
            )
        }
        return vertexList.toFloatArray()
    }

    private fun appendMeshVerticesForRange(
        samples: List<com.onyx.android.ink.ui.StrokeRenderPoint>,
        widths: List<Float>,
        style: StrokeStyle,
        range: IntRange,
        outVertices: MutableList<Float>,
    ) {
        val rangeSamples = samples.subList(range.first, range.last + 1)
        val rangeWidths = widths.subList(range.first, range.last + 1)
        val geometry = computeStrokeOutlineGeometry(rangeSamples, rangeWidths) ?: return
        if (geometry.count == 1) {
            val p = rangeSamples.first()
            val radius = (rangeWidths.firstOrNull() ?: style.baseWidth).coerceAtLeast(0.5f) / 2f
            outVertices += p.x - radius
            outVertices += p.y - radius
            outVertices += p.x + radius
            outVertices += p.y - radius
            outVertices += p.x - radius
            outVertices += p.y + radius
            outVertices += p.x + radius
            outVertices += p.y - radius
            outVertices += p.x + radius
            outVertices += p.y + radius
            outVertices += p.x - radius
            outVertices += p.y + radius
            return
        }
        val count = geometry.count
        for (index in 0 until count - 1) {
            val lx0 = geometry.leftX[index]
            val ly0 = geometry.leftY[index]
            val rx0 = geometry.rightX[index]
            val ry0 = geometry.rightY[index]
            val lx1 = geometry.leftX[index + 1]
            val ly1 = geometry.leftY[index + 1]
            val rx1 = geometry.rightX[index + 1]
            val ry1 = geometry.rightY[index + 1]

            outVertices += lx0
            outVertices += ly0
            outVertices += rx0
            outVertices += ry0
            outVertices += lx1
            outVertices += ly1

            outVertices += rx0
            outVertices += ry0
            outVertices += rx1
            outVertices += ry1
            outVertices += lx1
            outVertices += ly1
        }
    }

    private fun evictCommittedMeshBudget() {
        var totalBytes = committedStrokes.values.sumOf { it.mesh.byteSize.toLong() }
        if (totalBytes <= MAX_COMMITTED_MESH_BYTES) {
            return
        }

        val iterator = committedStrokes.entries.iterator()
        while (iterator.hasNext() && totalBytes > MAX_COMMITTED_MESH_BYTES) {
            val entry = iterator.next()
            totalBytes -= entry.value.mesh.byteSize
            deleteMesh(entry.value.mesh)
            iterator.remove()
        }
        rebuildSpatialIndex()
    }

    private fun deleteMesh(mesh: MeshRecord?) {
        if (mesh == null) {
            return
        }
        deleteBuffer(mesh.vboId)
    }

    private fun deleteBuffer(bufferId: Int) {
        if (bufferId == 0) {
            return
        }
        val ids = intArrayOf(bufferId)
        GLES20.glDeleteBuffers(1, ids, 0)
    }

    private fun createBufferId(): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        return ids[0]
    }

    private fun StrokeBounds.overlaps(rect: RectF): Boolean {
        val right = x + w
        val bottom = y + h
        return right >= rect.left && x <= rect.right && bottom >= rect.top && y <= rect.bottom
    }

    private fun computeBounds(points: List<StrokePoint>): StrokeBounds {
        if (points.isEmpty()) {
            return StrokeBounds(0f, 0f, 0f, 0f)
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        points.forEach { point ->
            minX = minOf(minX, point.x)
            minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x)
            maxY = maxOf(maxY, point.y)
        }
        return StrokeBounds(
            x = minX,
            y = minY,
            w = (maxX - minX).coerceAtLeast(0f),
            h = (maxY - minY).coerceAtLeast(0f),
        )
    }

    private fun recordFrameStats(frameStartNs: Long) {
        val frameEndNs = System.nanoTime()
        val durationNs = (frameEndNs - frameStartNs).coerceAtLeast(0L)

        frameDurationsNs[frameDurationIndex] = durationNs
        frameDurationIndex = (frameDurationIndex + 1) % frameDurationsNs.size
        frameDurationCount = (frameDurationCount + 1).coerceAtMost(frameDurationsNs.size)

        if (durationNs > JANK_FRAME_THRESHOLD_NS) {
            currentJankBurstNs += durationNs
            worstJankBurstNs = maxOf(worstJankBurstNs, currentJankBurstNs)
        } else {
            currentJankBurstNs = 0L
        }

        if (frameDurationCount < frameDurationsNs.size) {
            return
        }
        if (frameEndNs - lastMetricsLogNs < METRICS_LOG_INTERVAL_NS) {
            return
        }
        lastMetricsLogNs = frameEndNs

        val stats = snapshotFrameStats(frameEndNs)
        Log.d(
            TAG,
            "frameStats " +
                "p50=${stats.p50Ms}ms " +
                "p95=${stats.p95Ms}ms " +
                "jankBurst=${stats.worstJankBurstMs}ms " +
                "transformToFrame=${stats.transformToFrameMs}ms",
        )
    }

    private fun snapshotFrameStats(frameEndNs: Long): FrameStatsSnapshot {
        val snapshot = frameDurationsNs.copyOf(frameDurationCount)
        snapshot.sort()
        val p50 = snapshot[(snapshot.size * 0.5).toInt().coerceIn(0, snapshot.lastIndex)]
        val p95 = snapshot[(snapshot.size * 0.95).toInt().coerceIn(0, snapshot.lastIndex)]
        val transformLatency =
            if (latestTransformUpdateNanos > 0L) {
                (frameEndNs - latestTransformUpdateNanos).coerceAtLeast(0L)
            } else {
                0L
            }
        return FrameStatsSnapshot(
            p50Ms = p50.nanosToMillis(),
            p95Ms = p95.nanosToMillis(),
            worstJankBurstMs = worstJankBurstNs.nanosToMillis(),
            transformToFrameMs = transformLatency.nanosToMillis(),
        )
    }

    private fun Long.nanosToMillis(): Double = this.toDouble() / TimeUnit.MILLISECONDS.toNanos(1).toDouble()

    private fun packCellKey(
        x: Int,
        y: Int,
    ): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    private fun argbToRgba(
        color: Int,
        alphaMultiplier: Float,
    ): FloatArray {
        val a = ((color ushr 24) and 0xFF) / 255f
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val alpha = (a * alphaMultiplier).coerceIn(0f, 1f)
        return floatArrayOf(r, g, b, alpha)
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer
            .allocateDirect(size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(this@toFloatBuffer)
                position(0)
            }

    private fun GlStrokeInput.toStrokePoint(): StrokePoint =
        StrokePoint(
            x = x,
            y = y,
            t = eventTimeMillis,
            p = pressure,
            tx = tiltRadians * kotlin.math.cos(orientationRadians),
            ty = tiltRadians * kotlin.math.sin(orientationRadians),
            r = orientationRadians,
        )

    companion object {
        private const val TAG = "InkGlRenderer"
        private const val FLOAT_SIZE_BYTES = 4
        private const val SPATIAL_CELL_SIZE = 256f
        private const val CULL_MARGIN_SCREEN_PX = 96f
        private const val MAX_COMMITTED_MESH_BYTES = 48L * 1024L * 1024L
        private const val SELECTION_HIGHLIGHT_COLOR = 0xFF1E88E5.toInt()
        private const val SELECTION_HIGHLIGHT_ALPHA = 0.22f
        private const val LASSO_COLOR = 0xFF42A5F5.toInt()
        private const val LASSO_ALPHA = 0.9f
        private const val LASSO_LINE_WIDTH = 2.2f
        private const val HOVER_LINE_WIDTH = 1.6f
        private const val HOVER_CIRCLE_SEGMENTS = 28
        private const val TWO_PI = (Math.PI * 2.0).toFloat()
        private const val MIN_ZOOM_EPSILON = 0.0001f
        private const val JANK_FRAME_THRESHOLD_NS = 16_666_666L
        private val METRICS_LOG_INTERVAL_NS = TimeUnit.SECONDS.toNanos(2)
    }
}
