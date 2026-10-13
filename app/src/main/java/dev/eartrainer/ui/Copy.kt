package dev.eartrainer.ui

import dev.eartrainer.core.model.AppLanguage
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.ResolvedLanguage
import dev.eartrainer.core.voice.Prompts

object Copy {
    fun practice(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "תרגול" else "Practice"
    fun progress(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "התקדמות" else "Progress"
    fun notes(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "משוב" else "Notes"
    fun settings(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "הגדרות" else "Settings"

    fun eyebrow(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "מאמן שמיעה" else "Ear trainer"

    fun phaseTitle(phase: Phase, language: ResolvedLanguage): String = when (phase) {
        Phase.IDLE -> if (language == ResolvedLanguage.HEBREW) "מוכן כשתגידו" else "Ready when you are"
        Phase.PLAYING -> if (language == ResolvedLanguage.HEBREW) "הקשיבו" else "Listen"
        Phase.AWAITING_ANSWER -> if (language == ResolvedLanguage.HEBREW) "איזה מרווח?" else "Which interval?"
        Phase.SPEAKING -> if (language == ResolvedLanguage.HEBREW) "רגע" else "One moment"
        Phase.PAUSED -> if (language == ResolvedLanguage.HEBREW) "מושהה" else "Paused"
        Phase.AWAITING_CONFIRM -> if (language == ResolvedLanguage.HEBREW) "לאפס?" else "Reset progress?"
        Phase.AWAITING_FEEDBACK -> if (language == ResolvedLanguage.HEBREW) "אני מקשיב למשוב" else "Listening for feedback"
    }

    fun hint(phase: Phase, language: ResolvedLanguage): String = when (phase) {
        Phase.IDLE -> if (language == ResolvedLanguage.HEBREW) "אמרו \"התחל\". הידיים נשארות על ההגה." else "Say “start”. Hands stay on the wheel."
        Phase.AWAITING_ANSWER -> if (language == ResolvedLanguage.HEBREW) "אמרו את שם המרווח, או \"שוב\", \"דלג\", \"מה זה היה\"." else "Name it, or say “repeat”, “skip”, “what was that”."
        Phase.AWAITING_CONFIRM -> if (language == ResolvedLanguage.HEBREW) "אמרו \"אישור\" או \"ביטול\"." else "Say “confirm” or “cancel”."
        Phase.AWAITING_FEEDBACK -> if (language == ResolvedLanguage.HEBREW) "דברו, ואז אמרו \"שלח\"." else "Dictate, then say “send”."
        Phase.PAUSED -> if (language == ResolvedLanguage.HEBREW) "אמרו \"המשך\"." else "Say “resume”."
        else -> if (language == ResolvedLanguage.HEBREW) "אפשר לענות גם בלי להסתכל במסך." else "You can answer without looking at the screen."
    }

    fun start(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "התחל תרגול" else "Start practice"
    fun pause(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "השהה" else "Pause"
    fun resume(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "המשך" else "Resume"
    fun repeat(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "שוב" else "Repeat"
    fun reveal(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "תשובה" else "Reveal"
    fun skip(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "דלג" else "Skip"
    fun confirm(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "אישור" else "Confirm"
    fun cancel(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "ביטול" else "Cancel"
    fun heard(language: ResolvedLanguage, text: String) =
        if (language == ResolvedLanguage.HEBREW) "שמעתי: $text" else "Heard: $text"

    fun micTitle(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "צריך את המיקרופון פעם אחת" else "The microphone, once"

    fun micBody(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) {
            "אחרי האישור אפשר לתרגל בלי לגעת במסך, גם כשהמסך כבוי וגם בתוך אנדרואיד אוטו. הזיהוי רץ במכשיר דרך מנוע הדיבור של אנדרואיד."
        } else {
            "After this, practice stays hands-free with the screen off and in Android Auto. Speech uses the Android recognizer on this phone."
        }

    fun allowMic(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "לאפשר מיקרופון" else "Allow microphone"
    fun buttonsOnly(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "רק כפתורים, בלי קול" else "Buttons only, no voice"

    fun level(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "רמה" else "Level"
    fun direction(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "כיוון" else "Direction"
    fun instrument(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "כלי" else "Instrument"
    fun language(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "שפה" else "Language"
    fun tempo(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "קצב" else "Tempo"
    fun length(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "אורך תרגול" else "Session length"
    fun minutes(language: ResolvedLanguage, n: Int) = if (language == ResolvedLanguage.HEBREW) "$n דקות" else "$n min"
    fun adaptive(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "תרגול מסתגל" else "Adaptive practice"
    fun adaptiveHelp(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "מרווחים חלשים חוזרים מוקדם יותר." else "Weak intervals come back sooner."
    fun wake(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "מילת הפעלה" else "Wake phrase"
    fun wakeHelp(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "כשכבוי, פקודות נשמעות גם בלי \"מאמן שמיעה\"." else "Off means commands work without saying “ear trainer”."
    fun listening(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "המשך להקשיב" else "Keep listening"
    fun reset(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "אפס התקדמות" else "Reset progress"
    fun emptyStats(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "עדיין אין ניסיונות. אמרו התחל." else "No attempts yet. Say start."
    fun sessionScore(language: ResolvedLanguage, correct: Int, asked: Int) =
        if (language == ResolvedLanguage.HEBREW) "בתרגול הזה $correct מתוך $asked" else "This session $correct of $asked"
    fun emptyNotes(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "אין משוב שמור. אמרו \"שלח משוב\"." else "Nothing saved. Say “send feedback”."
    fun export(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "ייצוא" else "Export"
    fun saveNote(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "שמור הערה" else "Save note"
    fun noteHint(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "אפשר גם להקליד כשהרכב חונה" else "You can also type this while parked"
    fun connecting(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "פותח את ההאזנה…" else "Starting the listener…"

    fun crashTitle(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "האפליקציה קרסה" else "The app crashed"

    fun crashBody(language: ResolvedLanguage) =
        if (language == ResolvedLanguage.HEBREW) "אפשר לשתף את הדיווח הזה." else "You can share this report."

    fun crashShare(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "שתף דיווח" else "Share report"

    fun crashClear(language: ResolvedLanguage) = if (language == ResolvedLanguage.HEBREW) "נקה" else "Clear"

    fun levelName(set: IntervalSet, language: ResolvedLanguage) = Prompts.levelName(set, language)
    fun directionName(mode: DirectionMode, language: ResolvedLanguage) = Prompts.directionName(mode, language)
    fun instrumentName(instrument: Instrument, language: ResolvedLanguage) = Prompts.instrumentName(instrument, language)
    fun languageName(setting: AppLanguage, spoken: ResolvedLanguage) = Prompts.languageName(setting, spoken)
}
