package dev.eartrainer.core.practice

import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalMemory
import kotlin.random.Random

/**
 * Practice scheduler in the spirit of SM-2, sized for a drive rather than a day-long deck.
 * A miss is due again in about twenty seconds. A run of hits backs the interval off so
 * the session spends its time on what is still shaky.
 */
class AdaptiveScheduler {
    fun review(previous: IntervalMemory, correct: Boolean, nowMs: Long): IntervalMemory {
        if (!correct) {
            return previous.copy(
                attempts = previous.attempts + 1,
                streak = 0,
                lapses = previous.lapses + 1,
                ease = (previous.ease - 0.2).coerceAtLeast(MIN_EASE),
                repetitions = 0,
                intervalSeconds = LAPSE_SECONDS,
                dueAtEpochMs = nowMs + LAPSE_SECONDS * 1000,
                lastReviewedEpochMs = nowMs,
            )
        }
        val repetitions = previous.repetitions + 1
        val ease = (previous.ease + 0.08).coerceAtMost(MAX_EASE)
        val seconds = when (repetitions) {
            1 -> 40L
            2 -> 120L
            else -> (previous.intervalSeconds.coerceAtLeast(120) * ease)
                .toLong()
                .coerceAtMost(MAX_INTERVAL_SECONDS)
        }
        return previous.copy(
            attempts = previous.attempts + 1,
            correct = previous.correct + 1,
            streak = previous.streak + 1,
            ease = ease,
            repetitions = repetitions,
            intervalSeconds = seconds,
            dueAtEpochMs = nowMs + seconds * 1000,
            lastReviewedEpochMs = nowMs,
        )
    }

    fun weight(memory: IntervalMemory?, nowMs: Long, justAsked: Boolean): Double {
        if (memory == null || memory.attempts == 0) {
            return if (justAsked) 0.35 else NEW_ITEM_WEIGHT
        }
        val smoothed = (memory.correct + 1.0) / (memory.attempts + 2.0)
        val weakness = (1.0 - smoothed).coerceAtLeast(0.08)
        val overdueSeconds = (nowMs - memory.dueAtEpochMs) / 1000.0
        val dueFactor = if (overdueSeconds >= 0.0) {
            1.0 + (overdueSeconds.coerceAtMost(180.0) / 60.0)
        } else {
            0.3
        }
        val repeat = if (justAsked) 0.15 else 1.0
        return weakness * dueFactor * repeat
    }

    fun pick(
        pool: List<Interval>,
        memories: Map<Interval, IntervalMemory>,
        nowMs: Long,
        random: Random,
        avoid: Interval? = null,
    ): Interval {
        require(pool.isNotEmpty()) { "interval pool is empty" }
        if (pool.size == 1) return pool.first()
        val weights = pool.map { interval ->
            weight(memories[interval], nowMs, justAsked = interval == avoid).coerceAtLeast(0.01)
        }
        val total = weights.sum()
        var cursor = random.nextDouble() * total
        for (index in pool.indices) {
            cursor -= weights[index]
            if (cursor <= 0.0) return pool[index]
        }
        return pool.last()
    }

    companion object {
        const val MIN_EASE = 1.3
        const val MAX_EASE = 3.0
        const val LAPSE_SECONDS = 20L
        const val NEW_ITEM_WEIGHT = 2.4
        const val MAX_INTERVAL_SECONDS = 14L * 24L * 3600L
    }
}
