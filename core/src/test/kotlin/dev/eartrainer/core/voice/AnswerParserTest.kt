package dev.eartrainer.core.voice

import dev.eartrainer.core.model.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerParserTest {
    @Test
    fun englishNamesAndNumberForms() {
        assertInterval("perfect fifth", Interval.PERFECT_FIFTH)
        assertInterval("minor third", Interval.MINOR_THIRD)
        assertInterval("major third", Interval.MAJOR_THIRD)
        assertInterval("major 3rd", Interval.MAJOR_THIRD)
        assertInterval("minor 3rd", Interval.MINOR_THIRD)
        assertInterval("major 3", Interval.MAJOR_THIRD)
        assertInterval("perfect 5th", Interval.PERFECT_FIFTH)
        assertInterval("perfect 4", Interval.PERFECT_FOURTH)
        assertInterval("maj third", Interval.MAJOR_THIRD)
        assertInterval("min 2nd", Interval.MINOR_SECOND)
        assertInterval("it's a perfect fifth", Interval.PERFECT_FIFTH)
        assertInterval("I think minor sixth", Interval.MINOR_SIXTH)
        assertInterval("octave", Interval.PERFECT_OCTAVE)
        assertInterval("fifth", Interval.PERFECT_FIFTH)
        assertInterval("fourth", Interval.PERFECT_FOURTH)
        assertInterval("tritone", Interval.TRITONE)
        assertInterval("augmented fourth", Interval.TRITONE)
        assertInterval("diminished fifth", Interval.TRITONE)
        assertInterval("dim 5th", Interval.TRITONE)
        assertInterval("half step", Interval.MINOR_SECOND)
        assertInterval("whole step", Interval.MAJOR_SECOND)
        assertInterval("p5", Interval.PERFECT_FIFTH)
        assertInterval("P5", Interval.PERFECT_FIFTH)
        assertInterval("unison", Interval.UNISON)
    }

    @Test
    fun shortCodesKeepMajorAndMinorDistinct() {
        assertInterval("M3", Interval.MAJOR_THIRD)
        assertInterval("m3", Interval.MINOR_THIRD)
        assertInterval("M7", Interval.MAJOR_SEVENTH)
        assertInterval("m7", Interval.MINOR_SEVENTH)
        assertInterval("TT", Interval.TRITONE)
    }

    @Test
    fun ambiguousEnglishDoesNotGuess() {
        assertNull(AnswerParser.parse("third"))
        assertNull(AnswerParser.parse("second"))
        assertNull(AnswerParser.parse("sixth"))
        assertNull(AnswerParser.parse("seventh"))
        assertNull(AnswerParser.parse("major fifth"))
        assertNull(AnswerParser.parse("hello there"))
    }

    @Test
    fun hebrewNamesAndSynonyms() {
        assertInterval("טרצה גדולה", Interval.MAJOR_THIRD)
        assertInterval("טרצה קטנה", Interval.MINOR_THIRD)
        assertInterval("קוורטה זכה", Interval.PERFECT_FOURTH)
        assertInterval("קוורטה טהורה", Interval.PERFECT_FOURTH)
        assertInterval("קווינטה זכה", Interval.PERFECT_FIFTH)
        assertInterval("קווינטה טהורה", Interval.PERFECT_FIFTH)
        assertInterval("אוקטבה", Interval.PERFECT_OCTAVE)
        assertInterval("סקונדה קטנה", Interval.MINOR_SECOND)
        assertInterval("סקונדה גדולה", Interval.MAJOR_SECOND)
        assertInterval("טריטון", Interval.TRITONE)
        assertInterval("סקסטה קטנה", Interval.MINOR_SIXTH)
        assertInterval("סקסטה גדולה", Interval.MAJOR_SIXTH)
        assertInterval("ספטימה קטנה", Interval.MINOR_SEVENTH)
        assertInterval("ספטימה גדולה", Interval.MAJOR_SEVENTH)
        assertInterval("פרימה", Interval.UNISON)
        assertInterval("שליש גדול", Interval.MAJOR_THIRD)
        assertInterval("רביעית", Interval.PERFECT_FOURTH)
        assertInterval("חמישית", Interval.PERFECT_FIFTH)
        assertInterval("קוורטה מוגדלת", Interval.TRITONE)
        assertInterval("קווינטה מוקטנת", Interval.TRITONE)
        assertInterval("זה טרצה גדולה", Interval.MAJOR_THIRD)
        assertInterval("טְרִיטוֹן", Interval.TRITONE)
    }

    @Test
    fun ambiguousHebrewDoesNotGuess() {
        assertNull(AnswerParser.parse("טרצה"))
        assertNull(AnswerParser.parse("סקונדה"))
        assertNull(AnswerParser.parse("סקסטה"))
        assertNull(AnswerParser.parse("ספטימה"))
    }

    private fun assertInterval(phrase: String, interval: Interval) {
        val match = AnswerParser.parse(phrase)
        assertEquals(phrase, interval, match?.interval)
    }
}
