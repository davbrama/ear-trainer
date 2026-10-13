package dev.eartrainer.core.practice

import dev.eartrainer.core.model.AnswerModality
import dev.eartrainer.core.model.Direction
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Exercise
import dev.eartrainer.core.model.ExerciseType
import dev.eartrainer.core.model.ExpectedAnswer
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.NoteEvent
import dev.eartrainer.core.model.PracticeSettings
import kotlin.random.Random

object IntervalExercises {
    const val ROOT_LOW = 53
    const val ROOT_HIGH = 65

    fun pickRoot(
        interval: Interval,
        direction: Direction,
        random: Random,
        low: Int = ROOT_LOW,
        high: Int = ROOT_HIGH,
    ): Int {
        val span = interval.semitones
        val candidates = when (direction) {
            Direction.ASCENDING, Direction.HARMONIC -> (low..high).filter { it + span <= 81 }
            Direction.DESCENDING -> (low..high).filter { it - span >= 43 }
        }
        if (candidates.isEmpty()) return 60
        return candidates[random.nextInt(candidates.size)]
    }

    fun chooseDirection(mode: DirectionMode, random: Random): Direction = when (mode) {
        DirectionMode.ASCENDING -> Direction.ASCENDING
        DirectionMode.DESCENDING -> Direction.DESCENDING
        DirectionMode.HARMONIC -> Direction.HARMONIC
        DirectionMode.MIXED -> Direction.entries[random.nextInt(Direction.entries.size)]
    }

    fun naming(
        interval: Interval,
        direction: Direction,
        rootMidi: Int,
        tempo: Float,
        id: String,
    ): Exercise {
        val speed = tempo.coerceIn(0.5f, 2f)
        val duration = 0.7 / speed
        val gap = 0.34 / speed
        val other = when (direction) {
            Direction.ASCENDING, Direction.HARMONIC -> rootMidi + interval.semitones
            Direction.DESCENDING -> rootMidi - interval.semitones
        }
        val notes = if (direction == Direction.HARMONIC) {
            listOf(
                NoteEvent(rootMidi, 0.02, duration),
                NoteEvent(other, 0.02, duration),
            )
        } else {
            listOf(
                NoteEvent(rootMidi, 0.02, duration),
                NoteEvent(other, 0.02 + duration + gap, duration),
            )
        }
        return Exercise(
            id = id,
            type = ExerciseType.INTERVAL_NAMING,
            modality = AnswerModality.SPOKEN_NAME,
            direction = direction,
            rootMidi = rootMidi,
            notes = notes,
            expected = ExpectedAnswer.SpokenName(interval),
        )
    }

    /**
     * Future sing-back item: the app plays the root and expects the sung upper note.
     * [dev.eartrainer.core.practice.PracticeSession] does not deal these in v1.
     */
    fun singback(
        interval: Interval,
        rootMidi: Int,
        tempo: Float,
        id: String,
    ): Exercise {
        val speed = tempo.coerceIn(0.5f, 2f)
        val target = rootMidi + interval.semitones
        return Exercise(
            id = id,
            type = ExerciseType.INTERVAL_SINGBACK,
            modality = AnswerModality.SUNG_PITCH,
            direction = Direction.ASCENDING,
            rootMidi = rootMidi,
            notes = listOf(NoteEvent(rootMidi, 0.02, 0.85 / speed)),
            expected = ExpectedAnswer.SungPitch(midi = target, toleranceCents = 50.0),
        )
    }

    fun rebuild(exercise: Exercise, settings: PracticeSettings, random: Random): Exercise {
        val interval = exercise.trainedInterval ?: return exercise
        val direction = when (settings.directionMode) {
            DirectionMode.MIXED -> exercise.direction ?: Direction.ASCENDING
            DirectionMode.ASCENDING -> Direction.ASCENDING
            DirectionMode.DESCENDING -> Direction.DESCENDING
            DirectionMode.HARMONIC -> Direction.HARMONIC
        }
        val root = exercise.rootMidi?.let { candidate ->
            val fits = when (direction) {
                Direction.ASCENDING, Direction.HARMONIC -> candidate + interval.semitones <= 81
                Direction.DESCENDING -> candidate - interval.semitones >= 43
            }
            if (fits) candidate else pickRoot(interval, direction, random)
        } ?: pickRoot(interval, direction, random)
        return naming(interval, direction, root, settings.tempo, exercise.id)
    }
}
