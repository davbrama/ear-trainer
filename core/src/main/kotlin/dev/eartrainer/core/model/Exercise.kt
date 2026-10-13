package dev.eartrainer.core.model

/**
 * One prompt the learner hears, plus what kind of answer is expected.
 *
 * v1 only builds [ExerciseType.INTERVAL_NAMING]. Sing-back, chords, melodies, and
 * rhythm use the same shape so a new type is a new factory, not a new session.
 */
enum class ExerciseType {
    INTERVAL_NAMING,
    INTERVAL_SINGBACK,
    CHORD_NAMING,
    MELODY_DICTATION,
    RHYTHM_DICTATION,
}

/** How the learner answers. The mic path follows this, not the exercise name. */
enum class AnswerModality {
    /** Speech-to-text, then the interval lexicon. */
    SPOKEN_NAME,

    /** Raw mic PCM into a [dev.eartrainer.core.audio.PitchDetector]. */
    SUNG_PITCH,
}

data class NoteEvent(
    val midi: Int,
    val startSeconds: Double,
    val durationSeconds: Double,
)

sealed interface ExpectedAnswer {
    data class SpokenName(val interval: Interval) : ExpectedAnswer

    data class SungPitch(
        val midi: Int,
        val toleranceCents: Double = 50.0,
    ) : ExpectedAnswer
}

data class Exercise(
    val id: String,
    val type: ExerciseType,
    val modality: AnswerModality,
    val direction: Direction?,
    val rootMidi: Int?,
    val notes: List<NoteEvent>,
    val expected: ExpectedAnswer,
) {
    val trainedInterval: Interval?
        get() = (expected as? ExpectedAnswer.SpokenName)?.interval
}
