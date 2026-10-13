package dev.eartrainer.core.voice

import dev.eartrainer.core.model.AppLanguage
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.ResolvedLanguage
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.port.SpeechHypothesis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAndRouterTest {
    @Test
    fun everyCatalogPhraseParsesToItsAction() {
        for ((phrase, action) in IntentParser.catalog()) {
            val parsed = IntentParser.parse(phrase)
            assertEquals(phrase, action, parsed?.action)
        }
    }

    @Test
    fun englishAndHebrewCommands() {
        assertAction("start", VoiceAction.StartSession)
        assertAction("התחל", VoiceAction.StartSession)
        assertAction("pause", VoiceAction.Pause)
        assertAction("השהה", VoiceAction.Pause)
        assertAction("resume", VoiceAction.Resume)
        assertAction("המשך", VoiceAction.Resume)
        assertAction("stop", VoiceAction.StopSession)
        assertAction("עצור", VoiceAction.StopSession)
        assertAction("repeat", VoiceAction.Repeat)
        assertAction("חזור", VoiceAction.Repeat)
        assertAction("slower", VoiceAction.Slower)
        assertAction("יותר לאט", VoiceAction.Slower)
        assertAction("faster", VoiceAction.Faster)
        assertAction("מהר יותר", VoiceAction.Faster)
        assertAction("skip", VoiceAction.Skip)
        assertAction("דלג", VoiceAction.Skip)
        assertAction("what was that", VoiceAction.Reveal)
        assertAction("מה זה היה", VoiceAction.Reveal)
        assertAction("easier", VoiceAction.AdjustDifficulty(-1))
        assertAction("יותר קשה", VoiceAction.AdjustDifficulty(1))
        assertAction("how am I doing", VoiceAction.Stats)
        assertAction("מה המצב", VoiceAction.Stats)
        assertAction("what can I say", VoiceAction.Help)
        assertAction("עזרה", VoiceAction.Help)
        assertAction("beginner", VoiceAction.SetIntervalSet(IntervalSet.BEGINNER))
        assertAction("מתקדם", VoiceAction.SetIntervalSet(IntervalSet.ADVANCED))
        assertAction("ascending", VoiceAction.SetDirectionMode(DirectionMode.ASCENDING))
        assertAction("כיוון יורד", VoiceAction.SetDirectionMode(DirectionMode.DESCENDING))
        assertAction("harmonic", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC))
        assertAction("הרמוני", VoiceAction.SetDirectionMode(DirectionMode.HARMONIC))
        assertAction("piano", VoiceAction.SetInstrument(Instrument.PIANO))
        assertAction("פסנתר", VoiceAction.SetInstrument(Instrument.PIANO))
        assertAction("language hebrew", VoiceAction.SetLanguage(AppLanguage.HEBREW))
        assertAction("שפה אנגלית", VoiceAction.SetLanguage(AppLanguage.ENGLISH))
        assertAction("שפת המכשיר", VoiceAction.SetLanguage(AppLanguage.SYSTEM))
        assertAction("tempo 80", VoiceAction.SetTempo(0.8f))
        assertAction("קצב 120", VoiceAction.SetTempo(1.2f))
        assertAction("session 15 minutes", VoiceAction.SetSessionLength(15))
        assertAction("תרגול 10 דקות", VoiceAction.SetSessionLength(10))
        assertAction("reset progress", VoiceAction.RequestReset)
        assertAction("אפס התקדמות", VoiceAction.RequestReset)
        assertAction("please repeat", VoiceAction.Repeat)
        assertAction("stop listening", VoiceAction.SetListening(false))
        assertAction("תפסיק להקשיב", VoiceAction.SetListening(false))
        assertAction("send feedback", VoiceAction.BeginFeedback)
        assertAction("שלח משוב", VoiceAction.BeginFeedback)
    }

    @Test
    fun oneShotFeedbackKeepsTheDictatedText() {
        val parsed = IntentParser.parse("feedback the fifths feel muddy")
        val action = parsed?.action as VoiceAction.BeginFeedbackWithText
        assertEquals("the fifths feel muddy", action.text)
        val hebrew = IntentParser.parse("משוב הפסנתר חד מדי")?.action as VoiceAction.BeginFeedbackWithText
        assertEquals("הפסנתר חד מדי", hebrew.text)
    }

    @Test
    fun tempoOutOfRangeIsNotACommand() {
        assertNull(IntentParser.parse("tempo 10"))
        assertNull(IntentParser.parse("tempo 400"))
    }

    @Test
    fun answersWinWhileAQuestionIsOpenUnlessTheCommandIsGlobal() {
        val waiting = RouteContext(Phase.AWAITING_ANSWER, wakeRequired = false)
        val fifth = UtteranceRouter.route("perfect fifth", waiting)
        assertEquals(Interval.PERFECT_FIFTH, (fifth as RouteDecision.Answer).interval)
        val hebrew = UtteranceRouter.route("טרצה גדולה", waiting)
        assertEquals(Interval.MAJOR_THIRD, (hebrew as RouteDecision.Answer).interval)
        val numbered = UtteranceRouter.route("major 3rd", waiting)
        assertEquals(Interval.MAJOR_THIRD, (numbered as RouteDecision.Answer).interval)
        assertEquals(VoiceAction.Repeat, (UtteranceRouter.route("repeat", waiting) as RouteDecision.Command).action)
        assertEquals(VoiceAction.Repeat, (UtteranceRouter.route("חזור", waiting) as RouteDecision.Command).action)
        assertEquals(VoiceAction.StopSession, (UtteranceRouter.route("stop", waiting) as RouteDecision.Command).action)
        assertTrue(UtteranceRouter.route("yes", waiting) is RouteDecision.Unclear)
        assertEquals(VoiceAction.SetInstrument(Instrument.ORGAN), (UtteranceRouter.route("organ", waiting) as RouteDecision.Command).action)
    }

    @Test
    fun idleIgnoresIntervalNamesAndRequiresWakeWhenConfigured() {
        val idle = RouteContext(Phase.IDLE, wakeRequired = false)
        assertTrue(UtteranceRouter.route("perfect fifth", idle) is RouteDecision.Ignore)
        assertEquals(VoiceAction.StartSession, (UtteranceRouter.route("start", idle) as RouteDecision.Command).action)
        val gated = RouteContext(Phase.IDLE, wakeRequired = true)
        assertTrue(UtteranceRouter.route("start", gated) is RouteDecision.Ignore)
        assertEquals(
            VoiceAction.StartSession,
            (UtteranceRouter.route("ear trainer start", gated) as RouteDecision.Command).action,
        )
        assertEquals(
            VoiceAction.StartSession,
            (UtteranceRouter.route("מאמן שמיעה התחל", gated) as RouteDecision.Command).action,
        )
    }

    @Test
    fun confirmationAndFeedbackPhasesAreNarrow() {
        val confirm = RouteContext(Phase.AWAITING_CONFIRM, wakeRequired = false)
        assertEquals(VoiceAction.Confirm, (UtteranceRouter.route("yes", confirm) as RouteDecision.Command).action)
        assertEquals(VoiceAction.Confirm, (UtteranceRouter.route("אישור", confirm) as RouteDecision.Command).action)
        assertEquals(VoiceAction.Cancel, (UtteranceRouter.route("בטל", confirm) as RouteDecision.Command).action)
        assertTrue(UtteranceRouter.route("perfect fifth", confirm) is RouteDecision.Unclear)
        val feedback = RouteContext(Phase.AWAITING_FEEDBACK, wakeRequired = false)
        val captured = UtteranceRouter.route("the piano is a bit harsh", feedback) as RouteDecision.Command
        assertEquals(VoiceAction.CaptureFeedback("piano is a bit harsh"), captured.action)
        assertEquals(VoiceAction.SaveFeedback, (UtteranceRouter.route("send", feedback) as RouteDecision.Command).action)
        assertEquals(VoiceAction.SaveFeedback, (UtteranceRouter.route("שלח", feedback) as RouteDecision.Command).action)
    }

    @Test
    fun playbackOnlyHearsCommands() {
        val playing = RouteContext(Phase.PLAYING, wakeRequired = false)
        assertTrue(UtteranceRouter.route("blah blah", playing) is RouteDecision.Ignore)
        assertEquals(VoiceAction.Skip, (UtteranceRouter.route("skip", playing) as RouteDecision.Command).action)
    }

    @Test
    fun bestHypothesisWins() {
        val waiting = RouteContext(Phase.AWAITING_ANSWER, wakeRequired = false)
        val decision = UtteranceRouter.choose(
            listOf(
                SpeechHypothesis("banana", 0.4f),
                SpeechHypothesis("minor third", 0.9f),
            ),
            waiting,
        )
        assertEquals(Interval.MINOR_THIRD, (decision as RouteDecision.Answer).interval)
    }

    @Test
    fun everyPromptExistsInBothLanguages() {
        for (key in PromptKey.entries) {
            val english = Prompts.template(key, ResolvedLanguage.ENGLISH)
            val hebrew = Prompts.template(key, ResolvedLanguage.HEBREW)
            assertTrue(english.isNotBlank())
            assertTrue(hebrew.any { it in '\u0590'..'\u05FF' })
        }
        assertEquals(
            "Yes. major third.",
            Prompts.render(PromptKey.CORRECT, ResolvedLanguage.ENGLISH, mapOf("interval" to "MAJOR_THIRD")),
        )
        assertEquals(
            "נכון. טרצה גדולה.",
            Prompts.render(PromptKey.CORRECT, ResolvedLanguage.HEBREW, mapOf("interval" to "MAJOR_THIRD")),
        )
        assertTrue(
            Prompts.render(
                PromptKey.STATS,
                ResolvedLanguage.HEBREW,
                mapOf(
                    "correct" to "1",
                    "asked" to "2",
                    "lifetimeCorrect" to "3",
                    "lifetimeAttempts" to "4",
                    "weakest" to "TRITONE",
                ),
            ).contains("טריטון"),
        )
    }

    private fun assertAction(phrase: String, action: VoiceAction) {
        assertEquals(phrase, action, IntentParser.parse(phrase)?.action)
    }
}
