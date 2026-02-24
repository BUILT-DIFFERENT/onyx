package com.onyx.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val STRESS_BENCHMARK_PACKAGE = "com.onyx.android"
private const val STRESS_TIMEOUT_MS = 5000L

/**
 * PERF-04 scaffold:
 * stress path for large-note interactions and memory/frame pacing capture.
 */
@RunWith(AndroidJUnit4::class)
class LargeNoteStressBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun largeNotePanAndScrollStress() {
        benchmarkRule.measureRepeated(
            packageName = STRESS_BENCHMARK_PACKAGE,
            metrics = listOf(FrameTimingMetric(), MemoryUsageMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = null,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.textContains("Notes")), STRESS_TIMEOUT_MS)
            val noteList = device.findObject(By.scrollable(true))
            noteList?.fling(Direction.DOWN)
            noteList?.fling(Direction.UP)
            noteList?.findObject(By.clickable(true))?.click()
            device.wait(Until.hasObject(By.descContains("Pen")), STRESS_TIMEOUT_MS)

            val scrollable = device.findObject(By.scrollable(true))
            repeat(10) {
                scrollable?.scroll(Direction.DOWN, 1.0f)
                scrollable?.scroll(Direction.UP, 1.0f)
            }
        }
    }
}

