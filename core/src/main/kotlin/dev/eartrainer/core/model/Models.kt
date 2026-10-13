package dev.eartrainer.core.model

import kotlinx.serialization.Serializable

/** Pitch distance for v1. Later exercise types do not have to be one of these. */
enum class Interval(val semitones: Int) {
    UNISON(0),
    MINOR_SECOND(1),
    MAJOR_SECOND(2),
    MINOR_THIRD(3),
    MAJOR_THIRD(4),
    PERFECT_FOURTH(5),
    TRITONE(6),
    PERFECT_FIFTH(7),
    MINOR_SIXTH(8),
    MAJOR_SIXTH(9),
    MINOR_SEVENTH(10),
    MAJOR_SEVENTH(11),
    PERFECT_OCTAVE(12),
}

enum class Direction {
    ASCENDING,
    DESCENDING,
    HARMONIC,
}

enum class DirectionMode {
    ASCENDING,
    DESCENDING,
    HARMONIC,
    MIXED,
}

enum class Instrument {
    PIANO,
    SOFT,
    ORGAN,
    PLUCKED,
}

enum class IntervalSet {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    ;

    val intervals: Set<Interval>
        get() = when (this) {
            BEGINNER -> setOf(
                Interval.MINOR_THIRD,
                Interval.MAJOR_THIRD,
                Interval.PERFECT_FOURTH,
                Interval.PERFECT_FIFTH,
                Interval.PERFECT_OCTAVE,
            )
            INTERMEDIATE -> BEGINNER.intervals + setOf(
                Interval.MINOR_SECOND,
                Interval.MAJOR_SECOND,
                Interval.MINOR_SIXTH,
                Interval.MAJOR_SIXTH,
            )
            ADVANCED -> Interval.entries.toSet()
        }

    fun easier(): IntervalSet = when (this) {
        BEGINNER -> BEGINNER
        INTERMEDIATE -> BEGINNER
        ADVANCED -> INTERMEDIATE
    }

    fun harder(): IntervalSet = when (this) {
        BEGINNER -> INTERMEDIATE
        INTERMEDIATE -> ADVANCED
        ADVANCED -> ADVANCED
    }
}

@Serializable
enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    HEBREW,
}

enum class ResolvedLanguage {
    ENGLISH,
    HEBREW,
    ;

    fun tag(): String = if (this == HEBREW) "he-IL" else "en-US"
}

fun AppLanguage.resolve(systemLanguageTag: String): ResolvedLanguage = when (this) {
    AppLanguage.ENGLISH -> ResolvedLanguage.ENGLISH
    AppLanguage.HEBREW -> ResolvedLanguage.HEBREW
    AppLanguage.SYSTEM -> {
        val tag = systemLanguageTag.lowercase()
        if (tag.startsWith("he") || tag.startsWith("iw")) {
            ResolvedLanguage.HEBREW
        } else {
            ResolvedLanguage.ENGLISH
        }
    }
}

enum class Phase {
    IDLE,
    PLAYING,
    SPEAKING,
    AWAITING_ANSWER,
    AWAITING_CONFIRM,
    AWAITING_FEEDBACK,
    PAUSED,
}

@Serializable
data class PracticeSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val directionMode: DirectionMode = DirectionMode.MIXED,
    val instrument: Instrument = Instrument.PIANO,
    val tempo: Float = 1f,
    val intervalSet: IntervalSet = IntervalSet.BEGINNER,
    val sessionMinutes: Int = 10,
    val adaptive: Boolean = true,
    val wakeRequired: Boolean = false,
    val listenForCommands: Boolean = true,
) {
    fun normalized(): PracticeSettings = copy(
        tempo = tempo.coerceIn(0.5f, 2f),
        sessionMinutes = sessionMinutes.coerceIn(1, 120),
    )
}

@Serializable
data class IntervalMemory(
    val attempts: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
    val lapses: Int = 0,
    val ease: Double = 2.5,
    val repetitions: Int = 0,
    val intervalSeconds: Long = 0,
    val dueAtEpochMs: Long = 0,
    val lastReviewedEpochMs: Long = 0,
) {
    val accuracy: Double
        get() = if (attempts == 0) 0.0 else correct.toDouble() / attempts
}

@Serializable
data class FeedbackEntry(
    val id: String,
    val createdAtEpochMs: Long,
    val text: String,
    val languageTag: String,
)
