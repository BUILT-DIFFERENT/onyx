package com.onyx.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LATENCY_BENCHMARK_PACKAGE = "com.onyx.android"
private const val LATENCY_TIMEOUT_MS = 5000L

/**
 * PERF-01 scaffold:
 * captures trace-section timings around touch dispatch and GL frame rendering.
 */
@RunWith(AndroidJUnit4::class)
class InkLatencyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun stylusLatencyTraceSections() {
        benchmarkRule.measureRepeated(
            packageName = LATENCY_BENCHMARK_PACKAGE,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    TraceSectionMetric("InkCanvas#handleTouchEvent"),
                    TraceSectionMetric("InkGlRenderer#onDrawFrame"),
                ),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = null,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.textContains("Notes")), LATENCY_TIMEOUT_MS)
            val firstNote = device.findObject(By.clickable(true))
            firstNote?.click()
            device.wait(Until.hasObject(By.descContains("Pen")), LATENCY_TIMEOUT_MS)

            val editorSurface = device.findObject(By.descContains("Pen"))
            repeat(8) {
                editorSurface?.click()
            }
        }
    }
}

