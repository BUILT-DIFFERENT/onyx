package com.onyx.android.data.sync

import android.content.Context
import android.content.SharedPreferences

class LamportClock(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun next(): Long {
        val current = prefs.getLong(KEY_LAMPORT, 0L)
        val nextValue = current + 1
        prefs.edit().putLong(KEY_LAMPORT, nextValue).apply()
        return nextValue
    }

    fun peek(): Long = prefs.getLong(KEY_LAMPORT, 0L)

    fun updateIfGreater(receivedValue: Long) {
        var current: Long
        do {
            current = prefs.getLong(KEY_LAMPORT, 0L)
            if (receivedValue <= current) return
        } while (!prefs.edit().putLong(KEY_LAMPORT, receivedValue).commit())
    }

    companion object {
        private const val PREFS_NAME = "onyx_lamport_clock"
        private const val KEY_LAMPORT = "lamport_counter"
    }
}
