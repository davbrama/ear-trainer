package dev.eartrainer.core.voice

import dev.eartrainer.core.model.AppLanguage
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.VoiceAction

enum class CommandPriority {
    /** Wins over an interval answer: stop, repeat, skip, help, and the other driving controls. */
    GLOBAL,

    /** Settings. During a question they apply only when the utterance is not an interval. */
    NORMAL,

    /** Confirm, cancel, and send. Honored only in the matching phase. */
    PHASE_ONLY,
}

data class IntentMatch(
    val action: VoiceAction,
    val confidence: Float,
    val priority: CommandPriority,
)

/**
 * Full-utterance commands in English and Hebrew. Slot patterns cover tempo and
 * session length. Anything that is not a whole-phrase match falls through to the coach.
 */
object IntentParser {
    private data class Exact(
        val phrase: String,
        val action: VoiceAction,
        val priority: CommandPriority,
        val confidence: Float = 0.95f,
    )

    private data class Pattern(
        val regex: Regex,
        val priority: CommandPriority,
        val confidence: Float = 0.93f,
        val build: (MatchResult) -> VoiceAction?,
    )

    private val exact = listOf(
        Exact("start listening", VoiceAction.SetListening(true), CommandPriority.GLOBAL),
        Exact("stop listening", VoiceAction.SetListening(false), CommandPriority.GLOBAL),
        Exact("start", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("begin", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("lets go", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("lets practice", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("start practice", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("start session", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("התחל להקשיב", VoiceAction.SetListening(true), CommandPriority.GLOBAL),
        Exact("תתחיל להקשיב", VoiceAction.SetListening(true), CommandPriority.GLOBAL),
        Exact("תפסיק להקשיב", VoiceAction.SetListening(false), CommandPriority.GLOBAL),
        Exact("התחל", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("תתחיל", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("בוא נתחיל", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("התחל תרגול", VoiceAction.StartSession, CommandPriority.GLOBAL),
        Exact("pause", VoiceAction.Pause, CommandPriority.GLOBAL),
        Exact("hold on", VoiceAction.Pause, CommandPriority.GLOBAL),
        Exact("השהה", VoiceAction.Pause, CommandPriority.GLOBAL),
        Exact("השהייה", VoiceAction.Pause, CommandPriority.GLOBAL),
        Exact("רגע", VoiceAction.Pause, CommandPriority.GLOBAL),
        Exact("resume", VoiceAction.Resume, CommandPriority.GLOBAL),
        Exact("continue", VoiceAction.Resume, CommandPriority.GLOBAL),
        Exact("המשך", VoiceAction.Resume, CommandPriority.GLOBAL),
        Exact("תמשיך", VoiceAction.Resume, CommandPriority.GLOBAL),
        Exact("stop", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("stop session", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("end session", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("עצור", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("סיים", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("תפסיק", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("סיים תרגול", VoiceAction.StopSession, CommandPriority.GLOBAL),
        Exact("repeat", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("again", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("say that again", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("one more time", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("come again", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("שוב", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("חזור", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("תחזור", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("עוד פעם", VoiceAction.Repeat, CommandPriority.GLOBAL),
        Exact("slower", VoiceAction.Slower, CommandPriority.GLOBAL),
        Exact("slow down", VoiceAction.Slower, CommandPriority.GLOBAL),
        Exact("לאט יותר", VoiceAction.Slower, CommandPriority.GLOBAL),
        Exact("יותר לאט", VoiceAction.Slower, CommandPriority.GLOBAL),
        Exact("האט", VoiceAction.Slower, CommandPriority.GLOBAL),
        Exact("faster", VoiceAction.Faster, CommandPriority.GLOBAL),
        Exact("speed up", VoiceAction.Faster, CommandPriority.GLOBAL),
        Exact("מהר יותר", VoiceAction.Faster, CommandPriority.GLOBAL),
        Exact("יותר מהר", VoiceAction.Faster, CommandPriority.GLOBAL),
        Exact("תאיץ", VoiceAction.Faster, CommandPriority.GLOBAL),
        Exact("next", VoiceAction.Next, CommandPriority.GLOBAL),
        Exact("next question", VoiceAction.Next, CommandPriority.GLOBAL),
        Exact("הבא", VoiceAction.Next, CommandPriority.GLOBAL),
        Exact("שאלה הבאה", VoiceAction.Next, CommandPriority.GLOBAL),
        Exact("skip", VoiceAction.Skip, CommandPriority.GLOBAL),
        Exact("skip this", VoiceAction.Skip, CommandPriority.GLOBAL),
        Exact("דלג", VoiceAction.Skip, CommandPriority.GLOBAL),
        Exact("תדלג", VoiceAction.Skip, CommandPriority.GLOBAL),
        Exact("דלג על זה", VoiceAction.Skip, CommandPriority.GLOBAL),
        Exact("what was that", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("whats that", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("what is that", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("reveal", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("tell me", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("tell me the answer", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("מה זה היה", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("מה זה", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("מה התשובה", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("תגיד את התשובה", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("תגלה", VoiceAction.Reveal, CommandPriority.GLOBAL),
        Exact("easier", VoiceAction.AdjustDifficulty(-1), CommandPriority.GLOBAL),
        Exact("easier please", VoiceAction.AdjustDifficulty(-1), CommandPriority.GLOBAL),
        Exact("harder", VoiceAction.AdjustDifficulty(1), CommandPriority.GLOBAL),
        Exact("יותר קל", VoiceAction.AdjustDifficulty(-1), CommandPriority.GLOBAL),
        Exact("קל יותר", VoiceAction.AdjustDifficulty(-1), CommandPriority.GLOBAL),
        Exact("יותר קשה", VoiceAction.AdjustDifficulty(1), CommandPriority.GLOBAL),
        Exact("קשה יותר", VoiceAction.AdjustDifficulty(1), CommandPriority.GLOBAL),
        Exact("how am i doing", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("stats", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("statistics", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("my progress", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("מה המצב", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("איך אני", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("איך אני מסתדר", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("סטטיסטיקה", VoiceAction.Stats, CommandPriority.GLOBAL),
        Exact("help", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("what can i say", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("commands", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("עזרה", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("מה אפשר להגיד", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("מה אני יכול להגיד", VoiceAction.Help, CommandPriority.GLOBAL),
        Exact("reset", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("reset progress", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("reset lesson", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("clear progress", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("אפס התקדמות", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("אפס שיעור", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("מחק התקדמות", VoiceAction.RequestReset, CommandPriority.GLOBAL),
        Exact("beginner", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("level beginner", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("difficulty beginner", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("interval set beginner", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("מתחילים", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("רמה מתחילים", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("רמת מתחילים", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER), CommandPriority.NORMAL),
        Exact("intermediate", VoiceAction.SetIntervalSet(IntervalSet.INTERMEDIATE), CommandPriority.NORMAL),
        Exact("level intermediate", VoiceAction.SetIntervalSet(IntervalSet.INTERMEDIATE), CommandPriority.NORMAL),
        Exact("difficulty intermediate", VoiceAction.SetIntervalSet(IntervalSet.INTERMEDIATE), CommandPriority.NORMAL),
        Exact("בינוני", VoiceAction.SetIntervalSet(IntervalSet.INTERMEDIATE), CommandPriority.NORMAL),
        Exact("רמה בינונית", VoiceAction.SetIntervalSet(IntervalSet.INTERMEDIATE), CommandPriority.NORMAL),
        Exact("advanced", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED), CommandPriority.NORMAL),
        Exact("level advanced", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED), CommandPriority.NORMAL),
        Exact("difficulty advanced", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED), CommandPriority.NORMAL),
        Exact("מתקדם", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED), CommandPriority.NORMAL),
        Exact("רמה מתקדמת", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED), CommandPriority.NORMAL),
        Exact("ascending", VoiceAction.SetDirectionMode(DirectionMode.ASCENDING), CommandPriority.NORMAL),
        Exact("direction ascending", VoiceAction.SetDirectionMode(DirectionMode.ASCENDING), CommandPriority.NORMAL),
        Exact("עולה", VoiceAction.SetDirectionMode(DirectionMode.ASCENDING), CommandPriority.NORMAL),
        Exact("כיוון עולה", VoiceAction.SetDirectionMode(DirectionMode.ASCENDING), CommandPriority.NORMAL),
        Exact("descending", VoiceAction.SetDirectionMode(DirectionMode.DESCENDING), CommandPriority.NORMAL),
        Exact("direction descending", VoiceAction.SetDirectionMode(DirectionMode.DESCENDING), CommandPriority.NORMAL),
        Exact("יורד", VoiceAction.SetDirectionMode(DirectionMode.DESCENDING), CommandPriority.NORMAL),
        Exact("כיוון יורד", VoiceAction.SetDirectionMode(DirectionMode.DESCENDING), CommandPriority.NORMAL),
        Exact("harmonic", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC), CommandPriority.NORMAL),
        Exact("together", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC), CommandPriority.NORMAL),
        Exact("הרמוני", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC), CommandPriority.NORMAL),
        Exact("כיוון הרמוני", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC), CommandPriority.NORMAL),
        Exact("mixed", VoiceAction.SetDirectionMode(DirectionMode.MIXED), CommandPriority.NORMAL),
        Exact("direction mixed", VoiceAction.SetDirectionMode(DirectionMode.MIXED), CommandPriority.NORMAL),
        Exact("מעורב", VoiceAction.SetDirectionMode(DirectionMode.MIXED), CommandPriority.NORMAL),
        Exact("כיוון מעורב", VoiceAction.SetDirectionMode(DirectionMode.MIXED), CommandPriority.NORMAL),
        Exact("piano", VoiceAction.SetInstrument(Instrument.PIANO), CommandPriority.NORMAL),
        Exact("instrument piano", VoiceAction.SetInstrument(Instrument.PIANO), CommandPriority.NORMAL),
        Exact("פסנתר", VoiceAction.SetInstrument(Instrument.PIANO), CommandPriority.NORMAL),
        Exact("כלי פסנתר", VoiceAction.SetInstrument(Instrument.PIANO), CommandPriority.NORMAL),
        Exact("soft", VoiceAction.SetInstrument(Instrument.SOFT), CommandPriority.NORMAL),
        Exact("instrument soft", VoiceAction.SetInstrument(Instrument.SOFT), CommandPriority.NORMAL),
        Exact("צליל רך", VoiceAction.SetInstrument(Instrument.SOFT), CommandPriority.NORMAL),
        Exact("רך", VoiceAction.SetInstrument(Instrument.SOFT), CommandPriority.NORMAL),
        Exact("organ", VoiceAction.SetInstrument(Instrument.ORGAN), CommandPriority.NORMAL),
        Exact("instrument organ", VoiceAction.SetInstrument(Instrument.ORGAN), CommandPriority.NORMAL),
        Exact("אורגן", VoiceAction.SetInstrument(Instrument.ORGAN), CommandPriority.NORMAL),
        Exact("כלי אורגן", VoiceAction.SetInstrument(Instrument.ORGAN), CommandPriority.NORMAL),
        Exact("pluck", VoiceAction.SetInstrument(Instrument.PLUCKED), CommandPriority.NORMAL),
        Exact("plucked", VoiceAction.SetInstrument(Instrument.PLUCKED), CommandPriority.NORMAL),
        Exact("instrument plucked", VoiceAction.SetInstrument(Instrument.PLUCKED), CommandPriority.NORMAL),
        Exact("פריטה", VoiceAction.SetInstrument(Instrument.PLUCKED), CommandPriority.NORMAL),
        Exact("כלי פריטה", VoiceAction.SetInstrument(Instrument.PLUCKED), CommandPriority.NORMAL),
        Exact("english", VoiceAction.SetLanguage(AppLanguage.ENGLISH), CommandPriority.NORMAL),
        Exact("language english", VoiceAction.SetLanguage(AppLanguage.ENGLISH), CommandPriority.NORMAL),
        Exact("אנגלית", VoiceAction.SetLanguage(AppLanguage.ENGLISH), CommandPriority.NORMAL),
        Exact("שפה אנגלית", VoiceAction.SetLanguage(AppLanguage.ENGLISH), CommandPriority.NORMAL),
        Exact("hebrew", VoiceAction.SetLanguage(AppLanguage.HEBREW), CommandPriority.NORMAL),
        Exact("language hebrew", VoiceAction.SetLanguage(AppLanguage.HEBREW), CommandPriority.NORMAL),
        Exact("עברית", VoiceAction.SetLanguage(AppLanguage.HEBREW), CommandPriority.NORMAL),
        Exact("שפה עברית", VoiceAction.SetLanguage(AppLanguage.HEBREW), CommandPriority.NORMAL),
        Exact("device language", VoiceAction.SetLanguage(AppLanguage.SYSTEM), CommandPriority.NORMAL),
        Exact("language device", VoiceAction.SetLanguage(AppLanguage.SYSTEM), CommandPriority.NORMAL),
        Exact("match device", VoiceAction.SetLanguage(AppLanguage.SYSTEM), CommandPriority.NORMAL),
        Exact("שפת המכשיר", VoiceAction.SetLanguage(AppLanguage.SYSTEM), CommandPriority.NORMAL),
        Exact("שפת מכשיר", VoiceAction.SetLanguage(AppLanguage.SYSTEM), CommandPriority.NORMAL),
        Exact("wake word on", VoiceAction.SetWakeRequired(true), CommandPriority.NORMAL),
        Exact("require wake word", VoiceAction.SetWakeRequired(true), CommandPriority.NORMAL),
        Exact("wake word off", VoiceAction.SetWakeRequired(false), CommandPriority.NORMAL),
        Exact("דרוש מילת הפעלה", VoiceAction.SetWakeRequired(true), CommandPriority.NORMAL),
        Exact("בלי מילת הפעלה", VoiceAction.SetWakeRequired(false), CommandPriority.NORMAL),
        Exact("adaptive on", VoiceAction.SetAdaptive(true), CommandPriority.NORMAL),
        Exact("adaptive off", VoiceAction.SetAdaptive(false), CommandPriority.NORMAL),
        Exact("למידה מסתגלת", VoiceAction.SetAdaptive(true), CommandPriority.NORMAL),
        Exact("בלי למידה מסתגלת", VoiceAction.SetAdaptive(false), CommandPriority.NORMAL),
        Exact("send feedback", VoiceAction.BeginFeedback, CommandPriority.NORMAL),
        Exact("give feedback", VoiceAction.BeginFeedback, CommandPriority.NORMAL),
        Exact("שלח משוב", VoiceAction.BeginFeedback, CommandPriority.NORMAL),
        Exact("יש לי משוב", VoiceAction.BeginFeedback, CommandPriority.NORMAL),
        Exact("export feedback", VoiceAction.ExportFeedback, CommandPriority.NORMAL),
        Exact("share feedback", VoiceAction.ExportFeedback, CommandPriority.NORMAL),
        Exact("ייצא משוב", VoiceAction.ExportFeedback, CommandPriority.NORMAL),
        Exact("ייצוא משוב", VoiceAction.ExportFeedback, CommandPriority.NORMAL),
        Exact("confirm", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("yes", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("yes reset", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("confirm reset", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("אישור", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("אשר", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("כן", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("כן תאפס", VoiceAction.Confirm, CommandPriority.PHASE_ONLY),
        Exact("cancel", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("no", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("never mind", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("ביטול", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("בטל", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("לא", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("אל תאפס", VoiceAction.Cancel, CommandPriority.PHASE_ONLY),
        Exact("send", VoiceAction.SaveFeedback, CommandPriority.PHASE_ONLY),
        Exact("save", VoiceAction.SaveFeedback, CommandPriority.PHASE_ONLY),
        Exact("שלח", VoiceAction.SaveFeedback, CommandPriority.PHASE_ONLY),
        Exact("שמור", VoiceAction.SaveFeedback, CommandPriority.PHASE_ONLY),
    ).sortedByDescending { it.phrase.length }

    private val patterns = listOf(
        Pattern(Regex("""^(?:set )?tempo (?:to )?(\d{2,3})$"""), CommandPriority.NORMAL) { match ->
            tempo(match.groupValues[1])
        },
        Pattern(Regex("""^(?:קצב|טמפו) (\d{2,3})$"""), CommandPriority.NORMAL) { match ->
            tempo(match.groupValues[1])
        },
        Pattern(Regex("""^(?:session|practice)(?: length)? (\d{1,3}) minutes?$"""), CommandPriority.NORMAL) { match ->
            minutes(match.groupValues[1])
        },
        Pattern(Regex("""^(?:for )?(\d{1,3}) minutes?$"""), CommandPriority.NORMAL) { match ->
            minutes(match.groupValues[1])
        },
        Pattern(Regex("""^(?:אורך|תרגול) (\d{1,3}) דקות$"""), CommandPriority.NORMAL) { match ->
            minutes(match.groupValues[1])
        },
        Pattern(Regex("""^(?:feedback|send feedback) (.+)$"""), CommandPriority.NORMAL) { match ->
            VoiceAction.BeginFeedbackWithText(match.groupValues[1].trim())
        },
        Pattern(Regex("""^(?:משוב|שלח משוב) (.+)$"""), CommandPriority.NORMAL) { match ->
            VoiceAction.BeginFeedbackWithText(match.groupValues[1].trim())
        },
    )

    fun parse(raw: String): IntentMatch? {
        val text = TextNormalizer.stripFillers(TextNormalizer.normalize(raw))
        if (text.isEmpty()) return null
        exact.firstOrNull { it.phrase == text }?.let {
            return IntentMatch(it.action, it.confidence, it.priority)
        }
        for (pattern in patterns) {
            val match = pattern.regex.matchEntire(text) ?: continue
            val action = pattern.build(match) ?: continue
            return IntentMatch(action, pattern.confidence, pattern.priority)
        }
        return null
    }

    /** Phrases the README and the help test treat as the public command list. */
    fun catalog(): List<Pair<String, VoiceAction>> = exact.map { it.phrase to it.action }

    private fun tempo(raw: String): VoiceAction? {
        val value = raw.toIntOrNull() ?: return null
        if (value !in 40..200) return null
        return VoiceAction.SetTempo(value / 100f)
    }

    private fun minutes(raw: String): VoiceAction? {
        val value = raw.toIntOrNull() ?: return null
        if (value !in 1..120) return null
        return VoiceAction.SetSessionLength(value)
    }
}
