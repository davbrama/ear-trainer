package dev.eartrainer.core.practice

import dev.eartrainer.core.audio.Pitch
import dev.eartrainer.core.audio.PitchEstimate
import dev.eartrainer.core.model.AnswerModality
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.PracticeSettings
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.voice.PromptKey
import dev.eartrainer.core.voice.RouteDecision
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionTest {
    private var time = 1_000_000L

    @Test
    fun startSpeaksThenPlaysThenListens() {
        val session = session()
        val intro = session.onAction(VoiceAction.StartSession)
        assertEquals(PromptKey.SESSION_START, (intro.last() as Effect.Speak).key)
        val play = session.onSpeechFinished()
        assertTrue(play.last() is Effect.Play)
        assertEquals(Phase.PLAYING, session.snapshot().phase)
        val listen = session.onPlaybackFinished()
        assertTrue(listen.last() is Effect.Listen)
        assertEquals(Phase.AWAITING_ANSWER, session.snapshot().phase)
        assertEquals(AnswerModality.SPOKEN_NAME, session.snapshot().exercise?.modality)
        assertFalse(session.snapshot().showAnswer)
    }

    @Test
    fun correctAnswerAdvancesAndWrongAnswerReplaysTheName() {
        val session = session()
        openQuestion(session)
        val expected = session.snapshot().exercise!!.trainedInterval!!
        val wrong = Interval.entries.first { it != expected }
        val missed = session.onSpoken(wrong)
        assertEquals(PromptKey.INCORRECT, (missed.last() as Effect.Speak).key)
        assertEquals(expected.name, (missed.last() as Effect.Speak).args["interval"])
        assertTrue(missed.any { it is Effect.PersistProgress })
        assertEquals(1, session.snapshot().memories.getValue(expected).lapses)
        val replay = session.onSpeechFinished()
        assertEquals(session.snapshot().exercise?.id, (replay.last() as Effect.Play).exercise.id)
        assertTrue(session.snapshot().showAnswer)
        val next = session.onPlaybackFinished()
        assertTrue(next.last() is Effect.Play)

        openFromPlay(session)
        val answer = session.snapshot().exercise!!.trainedInterval!!
        val hit = session.onSpoken(answer)
        assertEquals(PromptKey.CORRECT, (hit.last() as Effect.Speak).key)
        assertEquals(1, session.snapshot().correct)
        session.onSpeechFinished()
        assertEquals(Phase.PLAYING, session.snapshot().phase)
    }

    @Test
    fun revealIsAMissAndSkipIsNot() {
        val session = session()
        openQuestion(session)
        val expected = session.snapshot().exercise!!.trainedInterval!!
        session.onAction(VoiceAction.Reveal)
        assertEquals(1, session.snapshot().memories.getValue(expected).lapses)
        val skippedBefore = session.snapshot().skipped
        session.onSpeechFinished()
        session.onPlaybackFinished()
        openFromPlay(session)
        session.onAction(VoiceAction.Skip)
        assertEquals(skippedBefore + 1, session.snapshot().skipped)
        assertEquals(1, session.snapshot().memories.getValue(expected).attempts)
    }

    @Test
    fun resetRequiresASpokenConfirmation() {
        val session = session()
        openQuestion(session)
        session.onSpoken(session.snapshot().exercise!!.trainedInterval!!)
        assertTrue(session.snapshot().memories.isNotEmpty() || session.snapshot().correct == 1)
        session.onSpeechFinished()
        val ask = session.onAction(VoiceAction.RequestReset)
        assertEquals(PromptKey.CONFIRM_RESET, (ask.last() as Effect.Speak).key)
        session.onSpeechFinished()
        assertEquals(Phase.AWAITING_CONFIRM, session.snapshot().phase)
        session.onAction(VoiceAction.Cancel)
        session.onSpeechFinished()
        assertTrue(session.snapshot().memories.isNotEmpty() || session.snapshot().asked > 0)
        session.onAction(VoiceAction.RequestReset)
        session.onSpeechFinished()
        val cleared = session.onAction(VoiceAction.Confirm)
        assertTrue(cleared.any { it is Effect.PersistProgress && it.memories.isEmpty() })
        assertEquals(PromptKey.RESET_DONE, (cleared.last() as Effect.Speak).key)
        session.onSpeechFinished()
        assertEquals(Phase.IDLE, session.snapshot().phase)
        assertTrue(session.snapshot().memories.isEmpty())
    }

    @Test
    fun settingsAreReachableAndReplayWithTheNewInstrument() {
        val session = session()
        openQuestion(session)
        val effects = session.onAction(VoiceAction.SetInstrument(Instrument.ORGAN))
        assertEquals(Instrument.ORGAN, session.snapshot().settings.instrument)
        assertTrue(effects.any { it is Effect.PersistSettings })
        assertEquals(PromptKey.INSTRUMENT_SET, (effects.last() as Effect.Speak).key)
        val replay = session.onSpeechFinished()
        assertTrue(replay.last() is Effect.Play)
        val easier = session.onAction(VoiceAction.AdjustDifficulty(-1))
        assertEquals(PromptKey.ALREADY_EASIEST, (easier.last() as Effect.Speak).key)
        session.onSpeechFinished()
        session.onAction(VoiceAction.SetIntervalSet(IntervalSet.ADVANCED))
        assertEquals(IntervalSet.ADVANCED, session.snapshot().settings.intervalSet)
    }

    @Test
    fun pauseResumeAndSessionLength() {
        val session = session(PracticeSettings(sessionMinutes = 1))
        openQuestion(session)
        session.onAction(VoiceAction.Pause)
        session.onSpeechFinished()
        assertEquals(Phase.PAUSED, session.snapshot().phase)
        val resumed = session.onAction(VoiceAction.Resume)
        assertEquals(PromptKey.RESUMED, (resumed.last() as Effect.Speak).key)
        val play = session.onSpeechFinished()
        assertTrue(play.last() is Effect.Play)
    }

    @Test
    fun sessionEndsWhenTimeIsUp() {
        val session = session(PracticeSettings(sessionMinutes = 1))
        openQuestion(session)
        val answer = session.snapshot().exercise!!.trainedInterval!!
        time += 61_000
        session.onSpoken(answer)
        val done = session.onSpeechFinished()
        assertEquals(PromptKey.SESSION_DONE, (done.last() as Effect.Speak).key)
    }

    @Test
    fun feedbackRoundTripAndUnclearPrompt() {
        val session = session()
        openQuestion(session)
        session.accept(RouteDecision.Unclear("hmm", 0.2f), "hmm")
        assertEquals(Phase.SPEAKING, session.snapshot().phase)
        session.onSpeechFinished()
        assertEquals(Phase.AWAITING_ANSWER, session.snapshot().phase)
        session.onAction(VoiceAction.BeginFeedback)
        session.onSpeechFinished()
        assertEquals(Phase.AWAITING_FEEDBACK, session.snapshot().phase)
        session.onAction(VoiceAction.CaptureFeedback("the piano is harsh"))
        session.onSpeechFinished()
        val saved = session.onAction(VoiceAction.SaveFeedback)
        assertTrue(saved.any { it is Effect.PersistFeedback && it.entry.text == "the piano is harsh" })
    }

    @Test
    fun singbackPitchGradesThroughTheSameSession() {
        val session = session()
        val exercise = IntervalExercises.singback(Interval.PERFECT_FIFTH, 60, 1f, "sing")
        session.awaitSingbackForTest(exercise)
        assertEquals(emptyList<Effect>(), session.onPitch(null))
        val hit = session.onPitch(PitchEstimate(Pitch.midiToHz(67), 0.9f))
        assertEquals(PromptKey.CORRECT, (hit.last() as Effect.Speak).key)
        val missSession = session()
        missSession.awaitSingbackForTest(exercise)
        val miss = missSession.onPitch(PitchEstimate(Pitch.midiToHz(60), 0.9f))
        assertEquals(PromptKey.INCORRECT, (miss.last() as Effect.Speak).key)
    }

    @Test
    fun helpIsAvailableFromIdle() {
        val session = session()
        val help = session.onAction(VoiceAction.Help)
        assertEquals(PromptKey.HELP, (help.last() as Effect.Speak).key)
        val listen = session.onSpeechFinished()
        assertTrue(listen.last() is Effect.Listen)
        assertNotNull(session.snapshot())
    }

    private fun session(settings: PracticeSettings = PracticeSettings()) = PracticeSession(
        settings = settings,
        random = Random(1),
        clock = { time },
    )

    private fun openQuestion(session: PracticeSession) {
        session.onAction(VoiceAction.StartSession)
        session.onSpeechFinished()
        session.onPlaybackFinished()
        assertEquals(Phase.AWAITING_ANSWER, session.snapshot().phase)
    }

    private fun openFromPlay(session: PracticeSession) {
        if (session.snapshot().phase == Phase.PLAYING) {
            session.onPlaybackFinished()
        }
        assertEquals(Phase.AWAITING_ANSWER, session.snapshot().phase)
    }
}
