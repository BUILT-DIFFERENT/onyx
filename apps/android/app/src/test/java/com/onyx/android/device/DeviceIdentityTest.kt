package com.onyx.android.device

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceIdentityTest {
    @Test
    fun `first call generates valid uuid`() {
        val harness = createHarness()
        val deviceIdentity = DeviceIdentity(harness.context)

        val id = deviceIdentity.getDeviceId()

        assertTrue(UUID_REGEX.matches(id))
    }

    @Test
    fun `second call returns same uuid`() {
        val harness = createHarness()
        val deviceIdentity = DeviceIdentity(harness.context)

        val firstId = deviceIdentity.getDeviceId()
        val secondId = deviceIdentity.getDeviceId()

        assertEquals(firstId, secondId)
    }

    @Test
    fun `new instance returns persisted uuid`() {
        val harness = createHarness()
        val firstIdentity = DeviceIdentity(harness.context)
        val firstId = firstIdentity.getDeviceId()

        val secondIdentity = DeviceIdentity(harness.context)
        val secondId = secondIdentity.getDeviceId()

        assertEquals(firstId, secondId)
        assertTrue(UUID_REGEX.matches(secondId))
    }

    private fun createHarness(): PrefsHarness {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        val harness = PrefsHarness(context, prefs, editor)

        every {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } returns prefs
        every { prefs.getString(KEY_DEVICE_ID, null) } answers { harness.storedId }
        every { prefs.edit() } returns editor
        every { editor.putString(KEY_DEVICE_ID, any()) } answers {
            harness.storedId = secondArg()
            editor
        }
        every { editor.apply() } returns Unit

        return harness
    }

    private class PrefsHarness(
        val context: Context,
        val prefs: SharedPreferences,
        val editor: SharedPreferences.Editor,
    ) {
        var storedId: String? = null
    }

    companion object {
        private const val PREFS_NAME = "onyx_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private val UUID_REGEX =
            Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            )
    }
}
