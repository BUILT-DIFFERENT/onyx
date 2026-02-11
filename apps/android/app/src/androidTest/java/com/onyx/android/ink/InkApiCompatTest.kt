package com.onyx.android.ink

import android.content.Context
import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.StockBrushes
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalInkCustomBrushApi::class)
class InkApiCompatTest {
    // Test brush: Stock pen brush with default settings
    private val testBrush: Brush =
        Brush.createWithColorIntArgb(
            StockBrushes.markerLatest,
            0xFF000000.toInt(),
            // 5mm brush
            5f,
            0.1f,
        )

    @Test
    fun inProgressStrokesView_instantiates_and_renders() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Test 1: Can instantiate without exception
        val view = InProgressStrokesView(context)
        assertNotNull(view)

        // Test 2: Can attach to app activity and accept touch input
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContentView(view)

            // Simulate touch event
            val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            val strokeId = view.startStroke(downEvent, 0, testBrush)

            // If we get here, InProgressStrokesView works
            assertNotNull(strokeId)

            view.cancelStroke(strokeId)
            downEvent.recycle()
        }
    }
}
