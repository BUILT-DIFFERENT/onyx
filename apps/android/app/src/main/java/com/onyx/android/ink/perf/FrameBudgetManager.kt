package com.onyx.android.ink.perf

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val MILLISECONDS_PER_SECOND = 1000L
private const val MIN_TARGET_FPS = 1
private const val DEFAULT_BUDGET_UTILIZATION = 0.8f
private const val MIN_EFFECTIVE_BUDGET_NANOS = 1L

class FrameBudgetManager(
    targetFps: Int = 60,
    private val budgetUtilization: Float = DEFAULT_BUDGET_UTILIZATION,
    private val nanoTimeProvider: () -> Long = System::nanoTime,
) {
    private val frameBudgetNanos: Long
    private val effectiveBudgetNanos: Long

    init {
        require(targetFps >= MIN_TARGET_FPS) { "targetFps must be >= $MIN_TARGET_FPS" }
        require(budgetUtilization > 0f && budgetUtilization <= 1f) {
            "budgetUtilization must be in (0f, 1f]"
        }
        frameBudgetNanos = NANOS_PER_MILLISECOND * MILLISECONDS_PER_SECOND / targetFps
        effectiveBudgetNanos =
            (frameBudgetNanos * budgetUtilization).toLong().coerceAtLeast(MIN_EFFECTIVE_BUDGET_NANOS)
    }

    fun <T> runWithinBudget(
        items: Iterable<T>,
        action: (T) -> Unit,
    ): Int {
        val frameStartNanos = nanoTimeProvider()
        var processedCount = 0
        for (item in items) {
            if (elapsedNanos(frameStartNanos) > effectiveBudgetNanos) {
                break
            }
            action(item)
            processedCount++
        }
        return processedCount
    }

    fun isOverBudget(frameStartNanos: Long): Boolean = elapsedNanos(frameStartNanos) > effectiveBudgetNanos

    fun frameBudgetMillis(): Float = frameBudgetNanos.toFloat() / NANOS_PER_MILLISECOND

    fun effectiveBudgetMillis(): Float = effectiveBudgetNanos.toFloat() / NANOS_PER_MILLISECOND

    private fun elapsedNanos(startNanos: Long): Long = nanoTimeProvider() - startNanos
}
