package com.onyx.android.device

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class DeviceIdentity(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun getDeviceId(): String {
        val existingId = prefs.getString(KEY_DEVICE_ID, null)
        if (existingId != null) {
            return existingId
        }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    companion object {
        private const val PREFS_NAME = "onyx_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
