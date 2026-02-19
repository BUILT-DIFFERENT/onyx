@file:Suppress("UnusedParameter", "EmptyFunctionBlock")

package com.onyx.android.ink.perf

object PerfInstrumentation {
    fun logTileQueueDepth(depth: Int) {}

    fun logTileStaleCancel(count: Int) {}

    fun logTileVisibleLatency(startNanos: Long) {}
}
