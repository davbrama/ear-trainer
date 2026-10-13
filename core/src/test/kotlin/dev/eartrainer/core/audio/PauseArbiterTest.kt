package dev.eartrainer.core.audio

import dev.eartrainer.core.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseArbiterTest {
    private var now = 1_000_000L
    private val arbiter = PauseArbiter(
        clock = { now },
        settleMs = 2_500,
        ttsTailMs = 1_500,
        recognizerLeadMs = 2_000,
    )

    @Test
    fun recognizerStartFocusLossDoesNotPauseAPlayingDrill() {
        arbiter.sessionStarted()
        now += 5_000
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        now += 300
        val decision = arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING)
        assertFalse(decision.pause)
        assertTrue(decision.reason.contains("RECOGNIZER"))
    }

    @Test
    fun everyRecognizerRestartGetsItsOwnWindow() {
        repeat(5) {
            now += 6_000
            arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
            now += 200
            assertFalse(arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.AWAITING_ANSWER).pause)
        }
    }

    @Test
    fun lateTtsLossDoesNotUseUpTheRecognizerWindow() {
        arbiter.clientStarted(InternalAudioClient.TTS)
        now += 1_200
        arbiter.clientStopped(InternalAudioClient.TTS)
        now += 50
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        now += 100
        // The TTS engine's loss arrives after onDone, while the recognizer is starting.
        assertFalse(arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING).pause)
        now += 400
        // Then the recognizer takes its own transient focus.
        assertFalse(arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING).pause)
        // Past the TTS tail, the recognizer window alone still covers.
        now += 1_100
        assertEquals(InternalAudioClient.RECOGNIZER, arbiter.attribute())
        assertFalse(arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING).pause)
    }

    @Test
    fun recognizerStartDoesNotCloseTheTtsTail() {
        arbiter.clientStarted(InternalAudioClient.TTS)
        now += 800
        arbiter.clientStopped(InternalAudioClient.TTS)
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        now += 2_100 // recognizer window over, TTS tail over too
        assertNull(arbiter.attribute())
        arbiter.clientStarted(InternalAudioClient.TTS)
        now += 100
        arbiter.clientStopped(InternalAudioClient.TTS)
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        now += 1_400
        assertEquals(InternalAudioClient.TTS, arbiter.attribute())
    }

    @Test
    fun ttsLossWhileSpeakingIsIgnored() {
        arbiter.clientStarted(InternalAudioClient.TTS)
        now += 10_000
        assertFalse(arbiter.decide(PauseSource.FOCUS_LOSS, Phase.SPEAKING).pause)
    }

    @Test
    fun externalTransientLossPausesOnceOwnWindowsAndSettleAreOver() {
        arbiter.sessionStarted()
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        now += 3_000
        val decision = arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.AWAITING_ANSWER)
        assertTrue(decision.pause)
        assertEquals("external focus loss", decision.reason)
    }

    @Test
    fun externalPermanentLossPauses() {
        now += 10_000
        assertTrue(arbiter.decide(PauseSource.FOCUS_LOSS, Phase.PLAYING).pause)
    }

    @Test
    fun focusLossRightAfterResumeIsDebounced() {
        arbiter.sessionStarted()
        now += 1_000
        val decision = arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING)
        assertFalse(decision.pause)
        assertTrue(decision.reason.startsWith("settling"))
        now += 2_000
        assertTrue(arbiter.decide(PauseSource.FOCUS_LOSS_TRANSIENT, Phase.PLAYING).pause)
    }

    @Test
    fun mediaControllerPauseIsDebouncedOnlyRightAfterStart() {
        arbiter.sessionStarted()
        now += 500
        assertFalse(arbiter.decide(PauseSource.MEDIA_CONTROLLER, Phase.PLAYING).pause)
        now += 2_500
        val decision = arbiter.decide(PauseSource.MEDIA_CONTROLLER, Phase.PLAYING)
        assertTrue(decision.pause)
        assertEquals("media controller", decision.reason)
    }

    @Test
    fun explicitPauseAlwaysWinsEvenRightAfterStartAndDuringOwnAudio() {
        arbiter.sessionStarted()
        arbiter.clientStarted(InternalAudioClient.TTS)
        arbiter.clientStarted(InternalAudioClient.RECOGNIZER)
        assertTrue(arbiter.decide(PauseSource.VOICE_COMMAND, Phase.PLAYING).pause)
        assertTrue(arbiter.decide(PauseSource.APP_BUTTON, Phase.AWAITING_ANSWER).pause)
        assertTrue(arbiter.decide(PauseSource.VOICE_COMMAND, Phase.SPEAKING).pause)
    }

    @Test
    fun nothingPausesWhenIdleOrAlreadyPaused() {
        now += 10_000
        for (source in PauseSource.entries) {
            assertFalse(arbiter.decide(source, Phase.IDLE).pause)
            assertFalse(arbiter.decide(source, Phase.PAUSED).pause)
        }
    }

    @Test
    fun ttsWithoutStopStaysAttributedUntilStopped() {
        arbiter.clientStarted(InternalAudioClient.TTS)
        now += 30_000
        assertEquals(InternalAudioClient.TTS, arbiter.attribute())
        arbiter.clientStopped(InternalAudioClient.TTS)
        now += 1_501
        assertNull(arbiter.attribute())
    }
}
