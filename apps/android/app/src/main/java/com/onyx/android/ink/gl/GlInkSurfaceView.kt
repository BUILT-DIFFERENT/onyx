@file:Suppress("MagicNumber")

package com.onyx.android.ink.gl

import android.content.Context
import android.graphics.Path
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.ViewTransform
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Suppress("TooManyFunctions")
internal class GlInkSurfaceView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : GLSurfaceView(context, attrs) {
        private val renderer = InkGlRenderer()
        private val strokeIdGenerator = AtomicLong(0L)
        private val finishedStrokeIds = ConcurrentHashMap<Long, Unit>()
        private val activeStrokeIds = ConcurrentHashMap<Long, Unit>()

        private var gestureRenderingActive = false
        private var strokeRenderingActive = false

        var maskPath: Path? = null

        init {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setZOrderOnTop(true)
            holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY
        }

        fun eagerInit() {
            // no-op; renderer initializes on first GL callback.
        }

        fun setCommittedStrokes(
            strokes: List<Stroke>,
            pageWidth: Float,
            pageHeight: Float,
        ) {
            queueEvent {
                renderer.setCommittedStrokes(
                    strokes = strokes,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                )
            }
            requestRender()
        }

        fun setOverlayState(overlayState: GlOverlayState) {
            renderer.setOverlayState(overlayState)
            requestRender()
        }

        fun setViewTransform(transform: ViewTransform) {
            renderer.setViewTransform(transform, System.nanoTime())
            requestRender()
        }

        fun updateScene(
            strokes: List<Stroke>,
            transform: ViewTransform,
            pageWidth: Float,
            pageHeight: Float,
        ) {
            setCommittedStrokes(strokes, pageWidth, pageHeight)
            setViewTransform(transform)
        }

        fun setGestureRenderingActive(isActive: Boolean) {
            if (gestureRenderingActive == isActive) {
                return
            }
            gestureRenderingActive = isActive
            syncRenderMode()
        }

        fun setStrokeRenderingActive(isActive: Boolean) {
            if (strokeRenderingActive == isActive) {
                return
            }
            strokeRenderingActive = isActive
            syncRenderMode()
        }

        private fun syncStrokeRenderingActive() {
            val shouldBeActive = activeStrokeIds.isNotEmpty()
            if (strokeRenderingActive == shouldBeActive) {
                return
            }
            strokeRenderingActive = shouldBeActive
            syncRenderMode()
        }

        private fun syncRenderMode() {
            val shouldRenderContinuously = gestureRenderingActive || strokeRenderingActive
            val targetMode = if (shouldRenderContinuously) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
            if (renderMode != targetMode) {
                renderMode = targetMode
            }
            if (!shouldRenderContinuously) {
                requestRender()
            }
        }

        fun startStroke(
            strokeInput: GlStrokeInput,
            brush: GlBrush,
        ): Long {
            val strokeId = strokeIdGenerator.incrementAndGet()
            activeStrokeIds[strokeId] = Unit
            syncStrokeRenderingActive()
            queueEvent {
                renderer.startStroke(strokeId, strokeInput, brush)
            }
            requestRender()
            return strokeId
        }

        fun addToStroke(
            strokeInput: GlStrokeInput,
            strokeId: Long,
        ) {
            queueEvent {
                renderer.addToStroke(strokeId, strokeInput)
            }
            requestRender()
        }

        fun finishStroke(
            strokeInput: GlStrokeInput,
            strokeId: Long,
        ) {
            finishedStrokeIds[strokeId] = Unit
            activeStrokeIds.remove(strokeId)
            syncStrokeRenderingActive()
            queueEvent {
                renderer.finishStroke(strokeId, strokeInput)
            }
            requestRender()
        }

        fun cancelStroke(
            strokeId: Long,
            @Suppress("UNUSED_PARAMETER")
            event: MotionEvent? = null,
        ) {
            finishedStrokeIds.remove(strokeId)
            activeStrokeIds.remove(strokeId)
            syncStrokeRenderingActive()
            queueEvent {
                renderer.cancelStroke(strokeId)
            }
            requestRender()
        }

        fun removeFinishedStrokes(strokeIds: Set<Long>) {
            strokeIds.forEach { strokeId ->
                finishedStrokeIds.remove(strokeId)
            }
            queueEvent {
                renderer.removeFinishedStrokes(strokeIds)
            }
            requestRender()
        }

        fun getFinishedStrokes(): Map<Long, Unit> = finishedStrokeIds.toMap()

        override fun onDetachedFromWindow() {
            queueEvent {
                renderer.release()
            }
            super.onDetachedFromWindow()
        }
    }
