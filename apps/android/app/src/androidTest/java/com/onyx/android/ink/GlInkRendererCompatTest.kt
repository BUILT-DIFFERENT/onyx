package com.onyx.android.ink

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.MainActivity
import com.onyx.android.ink.gl.GlBrush
import com.onyx.android.ink.gl.GlInkSurfaceView
import com.onyx.android.ink.gl.GlStrokeInput
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlInkRendererCompatTest {
    @Test
    fun glInkSurfaceView_instantiates_and_accepts_stroke_stream() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = GlInkSurfaceView(context)
        assertNotNull(view)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContentView(view)

            val brush = GlBrush(StrokeStyle(tool = Tool.PEN, baseWidth = 2f), 0xFF000000.toInt(), 1f)
            view.updateScene(strokes = emptyList(), transform = ViewTransform.DEFAULT, pageWidth = 100f, pageHeight = 100f)

            val downInput =
                GlStrokeInput(
                    x = 10f,
                    y = 10f,
                    eventTimeMillis = 0L,
                    toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS,
                    pressure = 0.5f,
                    tiltRadians = 0f,
                    orientationRadians = 0f,
                )
            val moveInput = downInput.copy(x = 20f, y = 18f, eventTimeMillis = 16L)
            val upInput = moveInput.copy(x = 24f, y = 22f, eventTimeMillis = 32L)

            val strokeId = view.startStroke(downInput, brush)
            view.addToStroke(moveInput, strokeId)
            view.finishStroke(upInput, strokeId)

            assertNotNull(strokeId)
        }
    }
}
