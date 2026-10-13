package dev.eartrainer.core.practice

import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalMemory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSchedulerTest {
    private val scheduler = AdaptiveScheduler()
    private val now = 1_700_000_000_000L

    @Test
    fun missIsDueAgainSoonAndDropsEase() {
        val updated = scheduler.review(IntervalMemory(ease = 2.5, repetitions = 3, intervalSeconds = 600), correct = false, nowMs = now)
        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.lapses)
        assertEquals(AdaptiveScheduler.LAPSE_SECONDS, updated.intervalSeconds)
        assertEquals(now + 20_000, updated.dueAtEpochMs)
        assertEquals(2.3, updated.ease, 0.001)
    }

    @Test
    fun easeDoesNotFallBelowTheFloor() {
        var memory = IntervalMemory()
        repeat(20) { memory = scheduler.review(memory, correct = false, nowMs = now) }
        assertEquals(AdaptiveScheduler.MIN_EASE, memory.ease, 0.001)
    }

    @Test
    fun correctReviewsGrowTheGap() {
        var memory = IntervalMemory()
        memory = scheduler.review(memory, correct = true, nowMs = now)
        assertEquals(40L, memory.intervalSeconds)
        memory = scheduler.review(memory, correct = true, nowMs = now)
        assertEquals(120L, memory.intervalSeconds)
        val third = scheduler.review(memory, correct = true, nowMs = now)
        assertTrue(third.intervalSeconds > memory.intervalSeconds)
        assertEquals(3, third.streak)
        assertEquals(3, third.correct)
    }

    @Test
    fun weakAndDueIntervalsOutweighMasteredOnes() {
        val weak = IntervalMemory(attempts = 8, correct = 1, ease = 1.4, dueAtEpochMs = now - 60_000)
        val strong = IntervalMemory(
            attempts = 8,
            correct = 8,
            ease = 2.8,
            repetitions = 4,
            intervalSeconds = 3_600,
            dueAtEpochMs = now + 3_600_000,
        )
        assertTrue(scheduler.weight(weak, now, justAsked = false) > scheduler.weight(strong, now, justAsked = false) * 3)
        assertTrue(scheduler.weight(null, now, justAsked = false) > scheduler.weight(null, now, justAsked = true))
    }

    @Test
    fun pickPrefersTheWeakInterval() {
        val weak = IntervalMemory(attempts = 10, correct = 1, ease = 1.3, dueAtEpochMs = now - 30_000)
        val strong = IntervalMemory(attempts = 10, correct = 10, ease = 2.8, repetitions = 5, intervalSeconds = 10_000, dueAtEpochMs = now + 10_000_000)
        val memories = mapOf(Interval.MINOR_THIRD to weak, Interval.PERFECT_FIFTH to strong)
        val counts = mutableMapOf<Interval, Int>()
        val random = Random(7)
        repeat(200) {
            val picked = scheduler.pick(
                listOf(Interval.MINOR_THIRD, Interval.PERFECT_FIFTH),
                memories,
                now,
                random,
            )
            counts[picked] = (counts[picked] ?: 0) + 1
        }
        assertTrue(counts.getValue(Interval.MINOR_THIRD) > counts.getValue(Interval.PERFECT_FIFTH))
    }

    @Test
    fun singleItemPoolIgnoresAvoid() {
        val only = scheduler.pick(listOf(Interval.PERFECT_FIFTH), emptyMap(), now, Random(1), avoid = Interval.PERFECT_FIFTH)
        assertEquals(Interval.PERFECT_FIFTH, only)
    }
}
