package dev.eartrainer.core.practice

import dev.eartrainer.core.audio.Pitch
import dev.eartrainer.core.audio.PitchEstimate
import dev.eartrainer.core.audio.SingbackGrader
import dev.eartrainer.core.model.AnswerModality
import dev.eartrainer.core.model.Direction
import dev.eartrainer.core.model.ExerciseType
import dev.eartrainer.core.model.ExpectedAnswer
import dev.eartrainer.core.model.Interval
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalAndGradeTest {
    @Test
    fun melodicAndHarmonicLayouts() {
        val up = IntervalExercises.naming(Interval.PERFECT_FIFTH, Direction.ASCENDING, 60, 1f, "a")
        assertEquals(60, up.notes[0].midi)
        assertEquals(67, up.notes[1].midi)
        assertTrue(up.notes[1].startSeconds > up.notes[0].startSeconds)

        val down = IntervalExercises.naming(Interval.MINOR_THIRD, Direction.DESCENDING, 64, 1f, "b")
        assertEquals(64, down.notes[0].midi)
        assertEquals(61, down.notes[1].midi)

        val both = IntervalExercises.naming(Interval.MAJOR_THIRD, Direction.HARMONIC, 60, 1f, "c")
        assertEquals(both.notes[0].startSeconds, both.notes[1].startSeconds, 0.001)
        assertEquals(64, both.notes[1].midi)
        assertEquals(AnswerModality.SPOKEN_NAME, both.modality)
        assertEquals(ExerciseType.INTERVAL_NAMING, both.type)
    }

    @Test
    fun fasterTempoShortensTheSecondNote() {
        val slow = IntervalExercises.naming(Interval.PERFECT_FOURTH, Direction.ASCENDING, 60, 0.5f, "s")
        val fast = IntervalExercises.naming(Interval.PERFECT_FOURTH, Direction.ASCENDING, 60, 2f, "f")
        assertTrue(fast.notes[1].startSeconds < slow.notes[1].startSeconds)
    }

    @Test
    fun singbackPlaysOnlyTheRootAndExpectsTheUpperPitch() {
        val exercise = IntervalExercises.singback(Interval.PERFECT_FOURTH, 60, 1f, "sing")
        assertEquals(ExerciseType.INTERVAL_SINGBACK, exercise.type)
        assertEquals(AnswerModality.SUNG_PITCH, exercise.modality)
        assertEquals(1, exercise.notes.size)
        assertEquals(60, exercise.notes.single().midi)
        val expected = exercise.expected as ExpectedAnswer.SungPitch
        assertEquals(65, expected.midi)
    }

    @Test
    fun singbackGraderAcceptsInTunePitchAndRejectsAnOctave() {
        val a4 = Pitch.midiToHz(69)
        assertTrue(SingbackGrader.grade(69, PitchEstimate(a4, 0.9f)))
        assertTrue(SingbackGrader.grade(69, PitchEstimate(a4 * 1.02, 0.8f)))
        assertFalse(SingbackGrader.grade(69, PitchEstimate(a4 * 2, 0.9f)))
        assertFalse(SingbackGrader.grade(69, PitchEstimate(a4, 0.2f)))
        assertFalse(SingbackGrader.grade(69, null))
    }

    @Test
    fun spokenAndSungResponsesUseTheMatchingModality() {
        val naming = IntervalExercises.naming(Interval.MAJOR_THIRD, Direction.ASCENDING, 60, 1f, "n")
        assertTrue(AnswerGrader.grade(naming, UserResponse.Speech(Interval.MAJOR_THIRD)).correct)
        assertFalse(AnswerGrader.grade(naming, UserResponse.Speech(Interval.MINOR_THIRD)).correct)
        val sing = IntervalExercises.singback(Interval.MAJOR_THIRD, 60, 1f, "s")
        val heard = PitchEstimate(Pitch.midiToHz(64), 0.95f)
        assertTrue(AnswerGrader.grade(sing, UserResponse.Sung(heard)).correct)
        assertFalse(AnswerGrader.grade(sing, UserResponse.Speech(Interval.MAJOR_THIRD)).correct)
    }

    @Test
    fun rootsStayInsideAComfortableRange() {
        val random = Random(3)
        repeat(30) {
            val root = IntervalExercises.pickRoot(Interval.PERFECT_OCTAVE, Direction.ASCENDING, random)
            assertTrue(root + 12 <= 81)
        }
    }
}
