package com.onyx.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val BENCHMARK_PACKAGE = "com.onyx.android"
private const val BENCHMARK_TIMEOUT_MS = 5000L

/**
 * PERF-02 scaffold:
 * - benchmark captures frame pacing during active editor panning
 * - policy targets are documented in docs/architecture/android-frame-rate-targets.md
 */
@RunWith(AndroidJUnit4::class)
class InkingFrameRateBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun editorPanFramePacing() {
        benchmarkRule.measureRepeated(
            packageName = BENCHMARK_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = null,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.textContains("Notes")), BENCHMARK_TIMEOUT_MS)
            val firstNote = device.findObject(By.clickable(true))
            firstNote?.click()
            device.wait(Until.hasObject(By.descContains("Pen")), BENCHMARK_TIMEOUT_MS)

            // Pan around the editor viewport to stress frame pacing under interaction.
            repeat(6) {
                val scrollable = device.findObject(By.scrollable(true))
                scrollable?.scroll(Direction.DOWN, 1.0f)
                scrollable?.scroll(Direction.UP, 1.0f)
            }
        }
    }
}

