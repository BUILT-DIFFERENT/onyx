package com.onyx.android.ink.gl

internal class EglController {
    @Volatile
    private var glThreadId: Long = -1L

    fun markGlThread() {
        glThreadId = Thread.currentThread().id
    }

    fun isOnGlThread(): Boolean = Thread.currentThread().id == glThreadId
}
