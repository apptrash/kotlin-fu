package com.example.for.audit

import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.pow

class ExponentialBackoff(
    val baseMs: Long = DEFAULT_BASE_MS,
    val multiplier: Float = DEFAULT_MULTIPLIER,
    val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    val jitterMs: Long = DEFAULT_JITTER_MS
) {
    companion object {
        const val DEFAULT_BASE_MS = 1000L
        const val DEFAULT_MULTIPLIER = 2f
        const val DEFAULT_MAX_DELAY_MS = 60000L
        const val DEFAULT_JITTER_MS = 500L
    }

    init {
        require(jitterMs in 1 until baseMs && multiplier > 1 && maxDelayMs > baseMs)
    }

    private val maxAttempt = ceil(log(base = multiplier.toDouble(), x = maxDelayMs.toDouble() / baseMs)).toInt()
    private var attempt = 0

    suspend fun delay() {
        val time = if (attempt == maxAttempt) maxDelayMs else baseMs * multiplier.pow(attempt).toLong()
        val jitterSign = if ((0..1).random() == 0) -1 else 1
        val jitter = jitterSign * (0..jitterMs).random()
        delay(time + jitter)
    }

    fun fail() {
        attempt = (attempt + 1).coerceAtMost(maxAttempt)
    }

    fun reset() {
        attempt = 0
    }
}
