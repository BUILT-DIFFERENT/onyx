package com.onyx.android.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        baselineProfileRule.collect(
            packageName = "com.onyx.android",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            device.waitForIdle(2000)
        }
    }

    @Test
    fun generateOpenNoteProfile() {
        baselineProfileRule.collect(
            packageName = "com.onyx.android",
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()

            device.waitForIdle(1000)

            pressHome()
        }
    }
}
