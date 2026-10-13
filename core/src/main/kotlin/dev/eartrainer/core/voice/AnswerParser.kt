package dev.eartrainer.core.voice

import dev.eartrainer.core.model.Interval

data class AnswerMatch(
    val interval: Interval,
    val confidence: Float,
)

/**
 * Data-driven interval names. English number forms ("major 3rd") go through the
 * quality/degree grammar. Hebrew goes through the noun table, including זכה alongside טהורה.
 * Case-sensitive short codes (M3 / m3) are matched before lowercasing.
 */
object AnswerParser {
    private val shortCodes = mapOf(
        "M2" to Interval.MAJOR_SECOND,
        "M3" to Interval.MAJOR_THIRD,
        "M6" to Interval.MAJOR_SIXTH,
        "M7" to Interval.MAJOR_SEVENTH,
        "m2" to Interval.MINOR_SECOND,
        "m3" to Interval.MINOR_THIRD,
        "m6" to Interval.MINOR_SIXTH,
        "m7" to Interval.MINOR_SEVENTH,
        "P1" to Interval.UNISON,
        "P4" to Interval.PERFECT_FOURTH,
        "P5" to Interval.PERFECT_FIFTH,
        "P8" to Interval.PERFECT_OCTAVE,
        "TT" to Interval.TRITONE,
        "A4" to Interval.TRITONE,
        "d5" to Interval.TRITONE,
        "D5" to Interval.TRITONE,
    )

    private enum class Quality { MINOR, MAJOR, PERFECT, AUGMENTED, DIMINISHED }

    private val qualities = mapOf(
        "minor" to Quality.MINOR,
        "major" to Quality.MAJOR,
        "perfect" to Quality.PERFECT,
        "augmented" to Quality.AUGMENTED,
        "diminished" to Quality.DIMINISHED,
        "min" to Quality.MINOR,
        "maj" to Quality.MAJOR,
        "aug" to Quality.AUGMENTED,
        "dim" to Quality.DIMINISHED,
    )

    private val degrees = mapOf(
        "unison" to 1,
        "prime" to 1,
        "first" to 1,
        "second" to 2,
        "third" to 3,
        "fourth" to 4,
        "fifth" to 5,
        "sixth" to 6,
        "seventh" to 7,
        "octave" to 8,
        "eighth" to 8,
        "1" to 1,
        "2" to 2,
        "3" to 3,
        "4" to 4,
        "5" to 5,
        "6" to 6,
        "7" to 7,
        "8" to 8,
    )

    private val phrases = mapOf(
        "unison" to pair(Interval.UNISON, 0.9f),
        "perfect prime" to pair(Interval.UNISON, 0.95f),
        "octave" to pair(Interval.PERFECT_OCTAVE, 0.9f),
        "fourth" to pair(Interval.PERFECT_FOURTH, 0.86f),
        "fifth" to pair(Interval.PERFECT_FIFTH, 0.86f),
        "tritone" to pair(Interval.TRITONE, 0.96f),
        "tri tone" to pair(Interval.TRITONE, 0.96f),
        "half step" to pair(Interval.MINOR_SECOND, 0.9f),
        "half tone" to pair(Interval.MINOR_SECOND, 0.9f),
        "semitone" to pair(Interval.MINOR_SECOND, 0.9f),
        "whole step" to pair(Interval.MAJOR_SECOND, 0.9f),
        "whole tone" to pair(Interval.MAJOR_SECOND, 0.9f),
        "p1" to pair(Interval.UNISON, 0.9f),
        "p4" to pair(Interval.PERFECT_FOURTH, 0.9f),
        "p5" to pair(Interval.PERFECT_FIFTH, 0.9f),
        "p8" to pair(Interval.PERFECT_OCTAVE, 0.9f),
        "tt" to pair(Interval.TRITONE, 0.9f),
    )

    private data class HebrewNoun(
        val names: Set<String>,
        val byQuality: Map<String, Interval>,
        val bare: Interval? = null,
    )

    private val secondQualities = mapOf(
        "קטנה" to Interval.MINOR_SECOND,
        "גדולה" to Interval.MAJOR_SECOND,
        "מינור" to Interval.MINOR_SECOND,
        "מזור" to Interval.MAJOR_SECOND,
        "קטן" to Interval.MINOR_SECOND,
        "גדול" to Interval.MAJOR_SECOND,
    )
    private val thirdQualities = mapOf(
        "קטנה" to Interval.MINOR_THIRD,
        "גדולה" to Interval.MAJOR_THIRD,
        "מינור" to Interval.MINOR_THIRD,
        "מזור" to Interval.MAJOR_THIRD,
        "קטן" to Interval.MINOR_THIRD,
        "גדול" to Interval.MAJOR_THIRD,
    )
    private val sixthQualities = mapOf(
        "קטנה" to Interval.MINOR_SIXTH,
        "גדולה" to Interval.MAJOR_SIXTH,
        "מינור" to Interval.MINOR_SIXTH,
        "מזור" to Interval.MAJOR_SIXTH,
        "קטן" to Interval.MINOR_SIXTH,
        "גדול" to Interval.MAJOR_SIXTH,
    )
    private val seventhQualities = mapOf(
        "קטנה" to Interval.MINOR_SEVENTH,
        "גדולה" to Interval.MAJOR_SEVENTH,
        "מינור" to Interval.MINOR_SEVENTH,
        "מזור" to Interval.MAJOR_SEVENTH,
        "קטן" to Interval.MINOR_SEVENTH,
        "גדול" to Interval.MAJOR_SEVENTH,
    )
    private val fourthQualities = mapOf(
        "זכה" to Interval.PERFECT_FOURTH,
        "טהורה" to Interval.PERFECT_FOURTH,
        "מושלמת" to Interval.PERFECT_FOURTH,
        "מוגדלת" to Interval.TRITONE,
    )
    private val fifthQualities = mapOf(
        "זכה" to Interval.PERFECT_FIFTH,
        "טהורה" to Interval.PERFECT_FIFTH,
        "מושלמת" to Interval.PERFECT_FIFTH,
        "מוקטנת" to Interval.TRITONE,
    )

    private val hebrewNouns = listOf(
        HebrewNoun(setOf("סקונדה", "שנייה", "שניה"), secondQualities),
        HebrewNoun(setOf("טרצה", "שליש", "שלישית"), thirdQualities),
        HebrewNoun(setOf("קוורטה", "קווארטה", "רביעית"), fourthQualities, Interval.PERFECT_FOURTH),
        HebrewNoun(setOf("קווינטה", "חמישית"), fifthQualities, Interval.PERFECT_FIFTH),
        HebrewNoun(setOf("סקסטה", "שישית"), sixthQualities),
        HebrewNoun(setOf("ספטימה", "שביעית"), seventhQualities),
        HebrewNoun(
            setOf("אוקטבה"),
            mapOf(
                "זכה" to Interval.PERFECT_OCTAVE,
                "טהורה" to Interval.PERFECT_OCTAVE,
                "מושלמת" to Interval.PERFECT_OCTAVE,
            ),
            Interval.PERFECT_OCTAVE,
        ),
        HebrewNoun(
            setOf("פרימה"),
            mapOf("טהורה" to Interval.UNISON, "זכה" to Interval.UNISON),
            Interval.UNISON,
        ),
    )

    private val hebrewAliases = mapOf(
        "טריטון" to Interval.TRITONE,
        "טריטונוס" to Interval.TRITONE,
        "אוניסון" to Interval.UNISON,
        "יוניסון" to Interval.UNISON,
        "יוניזון" to Interval.UNISON,
        "אוקטבה" to Interval.PERFECT_OCTAVE,
        "פרימה" to Interval.UNISON,
    )

    fun parse(raw: String): AnswerMatch? {
        val compact = raw.trim().trim('.')
        shortCodes[compact]?.let { return AnswerMatch(it, 0.97f) }
        var text = TextNormalizer.forAnswer(raw)
        text = TextNormalizer.stripFillers(text)
        if (text.isEmpty()) return null
        fromGrammar(text)?.let { return AnswerMatch(it, 0.95f) }
        fromHebrew(text)?.let { return AnswerMatch(it, 0.95f) }
        phrases[text]?.let { return AnswerMatch(it.first, it.second) }
        return null
    }

    private fun fromGrammar(text: String): Interval? {
        val parts = text.split(" ")
        if (parts.size != 2) return null
        val quality = qualities[parts[0]] ?: return null
        val degree = degrees[parts[1]] ?: return null
        return combine(quality, degree)
    }

    private fun combine(quality: Quality, degree: Int): Interval? = when (degree) {
        1 -> if (quality == Quality.PERFECT) Interval.UNISON else null
        2 -> when (quality) {
            Quality.MINOR -> Interval.MINOR_SECOND
            Quality.MAJOR -> Interval.MAJOR_SECOND
            else -> null
        }
        3 -> when (quality) {
            Quality.MINOR -> Interval.MINOR_THIRD
            Quality.MAJOR -> Interval.MAJOR_THIRD
            else -> null
        }
        4 -> when (quality) {
            Quality.PERFECT -> Interval.PERFECT_FOURTH
            Quality.AUGMENTED -> Interval.TRITONE
            else -> null
        }
        5 -> when (quality) {
            Quality.PERFECT -> Interval.PERFECT_FIFTH
            Quality.DIMINISHED -> Interval.TRITONE
            else -> null
        }
        6 -> when (quality) {
            Quality.MINOR -> Interval.MINOR_SIXTH
            Quality.MAJOR -> Interval.MAJOR_SIXTH
            else -> null
        }
        7 -> when (quality) {
            Quality.MINOR -> Interval.MINOR_SEVENTH
            Quality.MAJOR -> Interval.MAJOR_SEVENTH
            else -> null
        }
        8 -> if (quality == Quality.PERFECT) Interval.PERFECT_OCTAVE else null
        else -> null
    }

    private fun fromHebrew(text: String): Interval? {
        val parts = text.split(" ").filter { it.isNotBlank() }
        if (parts.size == 1) {
            hebrewAliases[parts[0]]?.let { return it }
            return hebrewNouns.firstOrNull { parts[0] in it.names }?.bare
        }
        if (parts.size != 2) return null
        val noun = hebrewNouns.firstOrNull { parts[0] in it.names || parts[1] in it.names } ?: return null
        val quality = if (parts[0] in noun.names) parts[1] else parts[0]
        return noun.byQuality[quality]
    }

    private fun pair(interval: Interval, confidence: Float) = interval to confidence
}
