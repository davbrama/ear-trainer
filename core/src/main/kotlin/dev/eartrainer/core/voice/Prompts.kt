package dev.eartrainer.core.voice

import dev.eartrainer.core.model.AppLanguage
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.ResolvedLanguage

enum class PromptKey {
    SESSION_START,
    CORRECT,
    INCORRECT,
    REVEAL,
    PAUSED,
    RESUMED,
    SESSION_DONE,
    DIDNT_CATCH,
    CONFIRM_RESET,
    RESET_DONE,
    RESET_CANCELLED,
    NOTHING_TO_CONFIRM,
    HELP,
    STATS,
    NO_STATS,
    FEEDBACK_ASK,
    FEEDBACK_GOT,
    FEEDBACK_SAVED,
    FEEDBACK_EMPTY,
    EXPORT_READY,
    LISTENING_ON,
    LISTENING_OFF,
    ALREADY_EASIEST,
    ALREADY_HARDEST,
    LEVEL_SET,
    DIRECTION_SET,
    INSTRUMENT_SET,
    LANGUAGE_SET,
    TEMPO_SET,
    LENGTH_SET,
    WAKE_ON,
    WAKE_OFF,
    ADAPTIVE_ON,
    ADAPTIVE_OFF,
    NEED_START,
    HEBREW_TTS_MISSING,
}

object Prompts {
    private val text: Map<PromptKey, Map<ResolvedLanguage, String>> = mapOf(
        PromptKey.SESSION_START to both(
            "Interval practice. Listen, then name what you hear.",
            "תרגול מרווחים. הקשיבו, ואז אמרו את שם המרווח.",
        ),
        PromptKey.CORRECT to both("Yes. {interval}.", "נכון. {interval}."),
        PromptKey.INCORRECT to both(
            "Not quite. That was {interval}.",
            "לא בדיוק. זה היה {interval}.",
        ),
        PromptKey.REVEAL to both("That was {interval}.", "זה היה {interval}."),
        PromptKey.PAUSED to both("Paused.", "מושהה."),
        PromptKey.RESUMED to both("Continuing.", "ממשיכים."),
        PromptKey.SESSION_DONE to both(
            "Session ended. {correct} correct out of {asked}.",
            "התרגול נגמר. {correct} נכון מתוך {asked}.",
        ),
        PromptKey.DIDNT_CATCH to both(
            "I didn't catch that. Name the interval, or say help.",
            "לא שמעתי. אמרו את המרווח, או אמרו עזרה.",
        ),
        PromptKey.CONFIRM_RESET to both(
            "Reset all progress on this phone? Say confirm, or cancel.",
            "לאפס את כל ההתקדמות בטלפון? אמרו אישור, או ביטול.",
        ),
        PromptKey.RESET_DONE to both("Progress reset.", "ההתקדמות אופסה."),
        PromptKey.RESET_CANCELLED to both("Okay, keeping your progress.", "בסדר, ההתקדמות נשארת."),
        PromptKey.NOTHING_TO_CONFIRM to both("Nothing is waiting for confirmation.", "אין עכשיו מה לאשר."),
        PromptKey.HELP to both(
            "Say start, pause, resume, or stop. Name the interval, or say repeat, skip, slower, faster, or what was that. Say easier, harder, how am I doing, send feedback, or help.",
            "אפשר לומר התחל, השהה, המשך או עצור. אמרו את שם המרווח, או אמרו שוב, דלג, לאט יותר, מהר יותר, או מה זה היה. אפשר גם יותר קל, יותר קשה, מה המצב, שלח משוב, או עזרה.",
        ),
        PromptKey.STATS to both(
            "This session, {correct} of {asked}. Overall {lifetimeCorrect} of {lifetimeAttempts}. Weakest: {weakest}.",
            "בתרגול הזה, {correct} מתוך {asked}. בסך הכול {lifetimeCorrect} מתוך {lifetimeAttempts}. הכי חלש: {weakest}.",
        ),
        PromptKey.NO_STATS to both(
            "No attempts yet. Say start when you are ready.",
            "עדיין אין ניסיונות. אמרו התחל כשאתם מוכנים.",
        ),
        PromptKey.FEEDBACK_ASK to both(
            "Tell me the note, then say send. Or say cancel.",
            "אמרו את המשוב, ואז אמרו שלח. או אמרו ביטול.",
        ),
        PromptKey.FEEDBACK_GOT to both(
            "Got it. Say send to save, or cancel.",
            "קיבלתי. אמרו שלח כדי לשמור, או ביטול.",
        ),
        PromptKey.FEEDBACK_SAVED to both("Saved on this phone.", "נשמר בטלפון."),
        PromptKey.FEEDBACK_EMPTY to both("I didn't hear any feedback.", "לא שמעתי משוב."),
        PromptKey.EXPORT_READY to both(
            "Feedback file is ready. Open the app to share it.",
            "קובץ המשוב מוכן. פתחו את האפליקציה כדי לשתף אותו.",
        ),
        PromptKey.LISTENING_ON to both("Listening for commands.", "מקשיב לפקודות."),
        PromptKey.LISTENING_OFF to both("I will stop listening.", "אפסיק להקשיב."),
        PromptKey.ALREADY_EASIEST to both("Already on the easiest set.", "כבר ברמה הקלה ביותר."),
        PromptKey.ALREADY_HARDEST to both("Already on the hardest set.", "כבר ברמה הקשה ביותר."),
        PromptKey.LEVEL_SET to both("Level set to {level}.", "הרמה עכשיו {level}."),
        PromptKey.DIRECTION_SET to both("Direction set to {direction}.", "הכיוון עכשיו {direction}."),
        PromptKey.INSTRUMENT_SET to both("Instrument set to {instrument}.", "הכלי עכשיו {instrument}."),
        PromptKey.LANGUAGE_SET to both("Language set to {language}.", "השפה עכשיו {language}."),
        PromptKey.TEMPO_SET to both("Tempo is now {value} percent.", "הקצב עכשיו {value} אחוז."),
        PromptKey.LENGTH_SET to both(
            "Session length set to {value} minutes.",
            "אורך התרגול עכשיו {value} דקות.",
        ),
        PromptKey.WAKE_ON to both(
            "Wake phrase required. Say ear trainer before a command.",
            "נדרשת מילת הפעלה. אמרו מאמן שמיעה לפני פקודה.",
        ),
        PromptKey.WAKE_OFF to both("Wake phrase is off.", "מילת ההפעלה כבויה."),
        PromptKey.ADAPTIVE_ON to both(
            "Adaptive practice is on. Weak intervals come up more often.",
            "התרגול המסתגל פעיל. מרווחים חלשים יופיעו יותר.",
        ),
        PromptKey.ADAPTIVE_OFF to both("Adaptive practice is off.", "התרגול המסתגל כבוי."),
        PromptKey.NEED_START to both("Say start to practice.", "אמרו התחל כדי לתרגל."),
        PromptKey.HEBREW_TTS_MISSING to both(
            "Hebrew text to speech is not installed in system settings.",
            "חסר קול עברי בהגדרות הדיבור של המערכת.",
        ),
    )

    fun template(key: PromptKey, language: ResolvedLanguage): String = text.getValue(key).getValue(language)

    fun render(
        key: PromptKey,
        language: ResolvedLanguage,
        args: Map<String, String> = emptyMap(),
    ): String {
        var rendered = template(key, language)
        for ((name, value) in args) {
            rendered = rendered.replace("{$name}", present(name, value, language))
        }
        return rendered
    }

    fun intervalName(interval: Interval, language: ResolvedLanguage): String = when (language) {
        ResolvedLanguage.ENGLISH -> when (interval) {
            Interval.UNISON -> "unison"
            Interval.MINOR_SECOND -> "minor second"
            Interval.MAJOR_SECOND -> "major second"
            Interval.MINOR_THIRD -> "minor third"
            Interval.MAJOR_THIRD -> "major third"
            Interval.PERFECT_FOURTH -> "perfect fourth"
            Interval.TRITONE -> "tritone"
            Interval.PERFECT_FIFTH -> "perfect fifth"
            Interval.MINOR_SIXTH -> "minor sixth"
            Interval.MAJOR_SIXTH -> "major sixth"
            Interval.MINOR_SEVENTH -> "minor seventh"
            Interval.MAJOR_SEVENTH -> "major seventh"
            Interval.PERFECT_OCTAVE -> "octave"
        }
        ResolvedLanguage.HEBREW -> when (interval) {
            Interval.UNISON -> "פרימה"
            Interval.MINOR_SECOND -> "סקונדה קטנה"
            Interval.MAJOR_SECOND -> "סקונדה גדולה"
            Interval.MINOR_THIRD -> "טרצה קטנה"
            Interval.MAJOR_THIRD -> "טרצה גדולה"
            Interval.PERFECT_FOURTH -> "קוורטה זכה"
            Interval.TRITONE -> "טריטון"
            Interval.PERFECT_FIFTH -> "קווינטה זכה"
            Interval.MINOR_SIXTH -> "סקסטה קטנה"
            Interval.MAJOR_SIXTH -> "סקסטה גדולה"
            Interval.MINOR_SEVENTH -> "ספטימה קטנה"
            Interval.MAJOR_SEVENTH -> "ספטימה גדולה"
            Interval.PERFECT_OCTAVE -> "אוקטבה"
        }
    }

    fun levelName(set: IntervalSet, language: ResolvedLanguage): String = when (language) {
        ResolvedLanguage.ENGLISH -> when (set) {
            IntervalSet.BEGINNER -> "beginner"
            IntervalSet.INTERMEDIATE -> "intermediate"
            IntervalSet.ADVANCED -> "advanced"
        }
        ResolvedLanguage.HEBREW -> when (set) {
            IntervalSet.BEGINNER -> "מתחילים"
            IntervalSet.INTERMEDIATE -> "בינוני"
            IntervalSet.ADVANCED -> "מתקדם"
        }
    }

    fun directionName(mode: DirectionMode, language: ResolvedLanguage): String = when (language) {
        ResolvedLanguage.ENGLISH -> when (mode) {
            DirectionMode.ASCENDING -> "ascending"
            DirectionMode.DESCENDING -> "descending"
            DirectionMode.HARMONIC -> "harmonic"
            DirectionMode.MIXED -> "mixed"
        }
        ResolvedLanguage.HEBREW -> when (mode) {
            DirectionMode.ASCENDING -> "עולה"
            DirectionMode.DESCENDING -> "יורד"
            DirectionMode.HARMONIC -> "הרמוני"
            DirectionMode.MIXED -> "מעורב"
        }
    }

    fun instrumentName(instrument: Instrument, language: ResolvedLanguage): String = when (language) {
        ResolvedLanguage.ENGLISH -> when (instrument) {
            Instrument.PIANO -> "piano"
            Instrument.SOFT -> "soft"
            Instrument.ORGAN -> "organ"
            Instrument.PLUCKED -> "plucked"
        }
        ResolvedLanguage.HEBREW -> when (instrument) {
            Instrument.PIANO -> "פסנתר"
            Instrument.SOFT -> "צליל רך"
            Instrument.ORGAN -> "אורגן"
            Instrument.PLUCKED -> "פריטה"
        }
    }

    fun languageName(languageSetting: AppLanguage, spoken: ResolvedLanguage): String = when (spoken) {
        ResolvedLanguage.ENGLISH -> when (languageSetting) {
            AppLanguage.ENGLISH -> "English"
            AppLanguage.HEBREW -> "Hebrew"
            AppLanguage.SYSTEM -> "match device"
        }
        ResolvedLanguage.HEBREW -> when (languageSetting) {
            AppLanguage.ENGLISH -> "אנגלית"
            AppLanguage.HEBREW -> "עברית"
            AppLanguage.SYSTEM -> "שפת המכשיר"
        }
    }

    private fun present(name: String, value: String, language: ResolvedLanguage): String = when (name) {
        "interval" -> runCatching { intervalName(Interval.valueOf(value), language) }.getOrDefault(value)
        "level" -> runCatching { levelName(IntervalSet.valueOf(value), language) }.getOrDefault(value)
        "direction" -> runCatching { directionName(DirectionMode.valueOf(value), language) }.getOrDefault(value)
        "instrument" -> runCatching { instrumentName(Instrument.valueOf(value), language) }.getOrDefault(value)
        "language" -> runCatching { languageName(AppLanguage.valueOf(value), language) }.getOrDefault(value)
        "weakest" -> if (value == "none" || value.isBlank()) {
            if (language == ResolvedLanguage.HEBREW) "אין עדיין" else "none yet"
        } else {
            value.split(",").filter { it.isNotBlank() }.joinToString(", ") {
                runCatching { intervalName(Interval.valueOf(it), language) }.getOrDefault(it)
            }
        }
        else -> value
    }

    private fun both(english: String, hebrew: String) = mapOf(
        ResolvedLanguage.ENGLISH to english,
        ResolvedLanguage.HEBREW to hebrew,
    )
}
