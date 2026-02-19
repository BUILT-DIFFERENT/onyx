package com.onyx.android.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.onyx.android"
private const val TIMEOUT_MS = 5000L

@RunWith(AndroidJUnit4::class)
class OpenNoteBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openNoteJourney() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = null,
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.textContains("Notes")), TIMEOUT_MS)

            val noteList =
                device.findObject(By.res(PACKAGE_NAME, "note_list"))
                    ?: device.findObject(By.scrollable(true))

            noteList?.let { list ->
                list.wait(Until.hasObject(By.clickable(true)), TIMEOUT_MS)
                val firstNote = list.findObject(By.clickable(true))
                firstNote?.click()
                device.wait(Until.hasObject(By.descContains("Pen")), TIMEOUT_MS)
            }
        }
    }
}

private fun MacrobenchmarkScope.pressHome() {
    device.pressHome()
    Thread.sleep(500)
}
