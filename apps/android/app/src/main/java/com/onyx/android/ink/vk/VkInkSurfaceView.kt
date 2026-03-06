@file:Suppress("MagicNumber")

package com.onyx.android.ink.vk

import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Trace
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.ViewTransform
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Suppress("TooManyFunctions")
internal class VkInkSurfaceView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : SurfaceView(context, attrs), SurfaceHolder.Callback {
        private val strokeIdGenerator = AtomicLong(0L)
        private val finishedStrokeIds = ConcurrentHashMap<Long, Unit>()
        private val activeStrokeIds = ConcurrentHashMap<Long, Unit>()

        private val renderThread = HandlerThread("VkInkRenderThread").apply { start() }
        private val renderHandler = Handler(renderThread.looper)

        private var nativeRendererHandle: Long = 0L
        private var gestureRenderingActive = false
        private var strokeRenderingActive = false
        private var frameScheduled = false
        private var continuousRendering = false
        private var surfaceReady = false
        private var rendererReady = false

        private var lastPageWidth: Float = 0f
        private var lastPageHeight: Float = 0f
        private var lastTransform: ViewTransform = ViewTransform.DEFAULT
        private var lastOverlayState: VkOverlayState = VkOverlayState()
        private var lastCommittedStrokeCount: Int = 0

        private val frameCallback =
            Choreographer.FrameCallback {
                frameScheduled = false
                renderFrame()
                if (continuousRendering) {
                    scheduleFrame()
                }
            }

        var maskPath: Path? = null
        var onRendererAvailabilityChanged: ((isAvailable: Boolean, errorMessage: String?) -> Unit)? = null

        var rendererErrorMessage: String? = null
            private set

        val isRendererAvailable: Boolean
            get() = rendererReady

        init {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this)
            eagerInit()
        }

        fun eagerInit() {
            if (nativeRendererHandle != 0L) {
                return
            }
            if (!VkNativeBridge.isSupported()) {
                updateRendererAvailability(
                    isAvailable = false,
                    errorMessage = "Vulkan is unavailable on this device; inking is disabled.",
                )
                return
            }
            nativeRendererHandle = VkNativeBridge.createRenderer()
            if (nativeRendererHandle == 0L) {
                updateRendererAvailability(
                    isAvailable = false,
                    errorMessage = "Vulkan renderer failed to initialize; inking is disabled.",
                )
            }
        }

        fun setCommittedStrokes(
            strokes: List<Stroke>,
            pageWidth: Float,
            pageHeight: Float,
        ) {
            lastCommittedStrokeCount = strokes.size
            lastPageWidth = pageWidth
            lastPageHeight = pageHeight
            enqueueRenderCall {
                VkNativeBridge.syncCommittedStrokes(nativeRendererHandle, strokes.size)
                VkNativeBridge.setPageSize(nativeRendererHandle, pageWidth, pageHeight)
            }
            requestRender()
        }

        fun setOverlayState(overlayState: VkOverlayState) {
            lastOverlayState = overlayState
            enqueueRenderCall {
                VkNativeBridge.setOverlayState(
                    handle = nativeRendererHandle,
                    selectedStrokeCount = overlayState.selectedStrokeIds.size,
                    lassoPointCount = overlayState.lassoPath.size,
                    hoverVisible = overlayState.hover.isVisible,
                    hoverX = overlayState.hover.screenX,
                    hoverY = overlayState.hover.screenY,
                    hoverRadius = overlayState.hover.screenRadius,
                    hoverColor = overlayState.hover.argbColor,
                    hoverAlpha = overlayState.hover.alpha,
                )
            }
            requestRender()
        }

        fun setViewTransform(transform: ViewTransform) {
            lastTransform = transform
            enqueueRenderCall {
                VkNativeBridge.setViewTransform(nativeRendererHandle, transform.zoom, transform.panX, transform.panY)
            }
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
            continuousRendering = gestureRenderingActive || strokeRenderingActive
            if (continuousRendering) {
                scheduleFrame()
            } else {
                requestRender()
            }
        }

        fun startStroke(
            strokeInput: VkStrokeInput,
            brush: VkBrush,
        ): Long {
            val strokeId = strokeIdGenerator.incrementAndGet()
            activeStrokeIds[strokeId] = Unit
            syncStrokeRenderingActive()
            enqueueRenderCall {
                VkNativeBridge.startStroke(nativeRendererHandle, strokeId, strokeInput, brush)
            }
            requestRender()
            return strokeId
        }

        fun addToStroke(
            strokeInput: VkStrokeInput,
            strokeId: Long,
        ) {
            enqueueRenderCall {
                VkNativeBridge.addStrokePoint(nativeRendererHandle, strokeId, strokeInput)
            }
            requestRender()
        }

        fun finishStroke(
            strokeInput: VkStrokeInput,
            strokeId: Long,
        ) {
            finishedStrokeIds[strokeId] = Unit
            activeStrokeIds.remove(strokeId)
            syncStrokeRenderingActive()
            enqueueRenderCall {
                VkNativeBridge.finishStroke(nativeRendererHandle, strokeId, strokeInput)
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
            enqueueRenderCall {
                VkNativeBridge.cancelStroke(nativeRendererHandle, strokeId)
            }
            requestRender()
        }

        fun removeFinishedStrokes(strokeIds: Set<Long>) {
            strokeIds.forEach { strokeId ->
                finishedStrokeIds.remove(strokeId)
            }
            enqueueRenderCall {
                VkNativeBridge.removeFinishedStrokes(nativeRendererHandle, strokeIds.toLongArray())
            }
            requestRender()
        }

        fun getFinishedStrokes(): Map<Long, Unit> = finishedStrokeIds.toMap()

        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            eagerInit()
            val surface = holder.surface ?: return
            if (nativeRendererHandle == 0L) {
                return
            }
            enqueueRenderCall {
                val initialized =
                    VkNativeBridge.initSurface(
                        handle = nativeRendererHandle,
                        surface = surface,
                        width = width.coerceAtLeast(1),
                        height = height.coerceAtLeast(1),
                    )
                if (!initialized) {
                    updateRendererAvailability(
                        isAvailable = false,
                        errorMessage = "Vulkan initialization failed; inking is disabled.",
                    )
                    return@enqueueRenderCall
                }
                VkNativeBridge.setPageSize(nativeRendererHandle, lastPageWidth, lastPageHeight)
                VkNativeBridge.setViewTransform(
                    nativeRendererHandle,
                    lastTransform.zoom,
                    lastTransform.panX,
                    lastTransform.panY,
                )
                VkNativeBridge.syncCommittedStrokes(nativeRendererHandle, lastCommittedStrokeCount)
                VkNativeBridge.setOverlayState(
                    handle = nativeRendererHandle,
                    selectedStrokeCount = lastOverlayState.selectedStrokeIds.size,
                    lassoPointCount = lastOverlayState.lassoPath.size,
                    hoverVisible = lastOverlayState.hover.isVisible,
                    hoverX = lastOverlayState.hover.screenX,
                    hoverY = lastOverlayState.hover.screenY,
                    hoverRadius = lastOverlayState.hover.screenRadius,
                    hoverColor = lastOverlayState.hover.argbColor,
                    hoverAlpha = lastOverlayState.hover.alpha,
                )
                updateRendererAvailability(isAvailable = true, errorMessage = null)
                requestRender()
            }
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            if (!surfaceReady || nativeRendererHandle == 0L) {
                return
            }
            enqueueRenderCall {
                VkNativeBridge.resize(nativeRendererHandle, width.coerceAtLeast(1), height.coerceAtLeast(1))
            }
            requestRender()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            updateRendererAvailability(isAvailable = false, errorMessage = rendererErrorMessage)
            if (nativeRendererHandle == 0L) {
                return
            }
            enqueueRenderCall {
                VkNativeBridge.destroySurface(nativeRendererHandle)
            }
        }

        fun requestRender() {
            scheduleFrame()
        }

        private fun scheduleFrame() {
            if (frameScheduled || !surfaceReady || nativeRendererHandle == 0L) {
                return
            }
            frameScheduled = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        private fun renderFrame() {
            if (!surfaceReady || nativeRendererHandle == 0L) {
                return
            }
            enqueueRenderCall {
                Trace.beginSection("VkInkRenderer#drawFrame")
                try {
                    val drawn = VkNativeBridge.drawFrame(nativeRendererHandle)
                    if (!drawn) {
                        updateRendererAvailability(
                            isAvailable = false,
                            errorMessage = "Vulkan draw failed; inking is disabled.",
                        )
                    }
                } finally {
                    Trace.endSection()
                }
            }
        }

        private fun enqueueRenderCall(block: () -> Unit) {
            if (nativeRendererHandle == 0L) {
                return
            }
            renderHandler.post {
                try {
                    block()
                } catch (
                    @Suppress("TooGenericExceptionCaught") throwable: Throwable,
                ) {
                    Log.e(TAG, "Renderer call failed", throwable)
                    updateRendererAvailability(
                        isAvailable = false,
                        errorMessage = "Vulkan renderer error; inking is disabled.",
                    )
                }
            }
        }

        private fun updateRendererAvailability(
            isAvailable: Boolean,
            errorMessage: String?,
        ) {
            rendererReady = isAvailable
            rendererErrorMessage = errorMessage
            post {
                onRendererAvailabilityChanged?.invoke(isAvailable, errorMessage)
            }
        }

        override fun onDetachedFromWindow() {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            if (nativeRendererHandle != 0L) {
                val handle = nativeRendererHandle
                nativeRendererHandle = 0L
                renderHandler.post {
                    VkNativeBridge.destroySurface(handle)
                    VkNativeBridge.destroyRenderer(handle)
                }
            }
            renderThread.quitSafely()
            super.onDetachedFromWindow()
        }

        companion object {
            private const val TAG = "VkInkSurfaceView"
        }
    }
