package dev.eartrainer.core.audio

import dev.eartrainer.core.model.AnswerModality
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.NoteEvent
import dev.eartrainer.core.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneAndPolicyTest {
    private val synth = ToneSynthesizer(sampleRate = 16_000)

    @Test
    fun pianoFundamentalDominatesNearbyFrequencies() {
        val note = synth.render(listOf(NoteEvent(69, 0.0, 1.0)), Instrument.PIANO)
        val target = goertzel(note, 16_000, 440.0)
        val offHarmonic = goertzel(note, 16_000, 440.0 * 1.5)
        val rumble = goertzel(note, 16_000, 80.0)
        assertTrue(target > offHarmonic * 2)
        assertTrue(target > rumble)
    }

    @Test
    fun softVoiceAlsoCentersOnTheWrittenPitch() {
        val note = synth.render(listOf(NoteEvent(60, 0.0, 0.8)), Instrument.SOFT)
        val fundamental = goertzel(note, 16_000, Pitch.midiToHz(60))
        val neighbor = goertzel(note, 16_000, Pitch.midiToHz(63))
        assertTrue(fundamental > neighbor)
    }

    @Test
    fun harmonicIntervalContainsBothPitches() {
        val both = synth.render(
            listOf(NoteEvent(60, 0.0, 0.8), NoteEvent(67, 0.0, 0.8)),
            Instrument.ORGAN,
        )
        val lower = goertzel(both, 16_000, Pitch.midiToHz(60))
        val upper = goertzel(both, 16_000, Pitch.midiToHz(67))
        val absent = goertzel(both, 16_000, 90.0)
        assertTrue(lower > absent)
        assertTrue(upper > absent)
    }

    @Test
    fun pluckedVoiceIsNotSilent() {
        val note = synth.render(listOf(NoteEvent(64, 0.0, 0.6)), Instrument.PLUCKED)
        assertTrue(note.any { kotlin.math.abs(it.toInt()) > 500 })
    }

    @Test
    fun wavHeaderMatchesThePcmLength() {
        val pcm = shortArrayOf(0, 1000, -1000, 0)
        val bytes = Wav.encode(pcm, 16_000)
        assertEquals("RIFF", bytes.decodeToString(0, 4))
        assertEquals("WAVE", bytes.decodeToString(8, 12))
        assertEquals(16_000, java.nio.ByteBuffer.wrap(bytes, 24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(pcm.size * 2, bytes.size - 44)
    }

    @Test
    fun speechNeverSharesTheMicAndPitchNeverOverlapsPlayback() {
        Phase.entries.forEach { phase ->
            AnswerModality.entries.forEach { modality ->
                listOf(true, false).forEach { listening ->
                    val plan = AudioTurnPolicy.plan(phase, modality, listening)
                    if (plan.output == OutputIntent.SPEECH) {
                        assertEquals(MicMode.NONE, plan.mic)
                    }
                    if (plan.output != OutputIntent.NONE) {
                        assertNotEquals(MicMode.PITCH_PCM, plan.mic)
                    }
                }
            }
        }
    }

    @Test
    fun sungAnswersUsePcmAndSpokenAnswersUseSpeech() {
        val sung = AudioTurnPolicy.plan(Phase.AWAITING_ANSWER, AnswerModality.SUNG_PITCH, keepListening = true)
        val spoken = AudioTurnPolicy.plan(Phase.AWAITING_ANSWER, AnswerModality.SPOKEN_NAME, keepListening = true)
        assertEquals(MicMode.PITCH_PCM, sung.mic)
        assertEquals(OutputIntent.NONE, sung.output)
        assertEquals(MicMode.SPEECH, spoken.mic)
        assertEquals(FocusRequest.HOLD_MEDIA, spoken.focus)
    }

    @Test
    fun promptsReleaseTheMicAndPlaybackKeepsACommandMic() {
        val speaking = AudioTurnPolicy.plan(Phase.SPEAKING, AnswerModality.SPOKEN_NAME, true)
        val playing = AudioTurnPolicy.plan(Phase.PLAYING, AnswerModality.SPOKEN_NAME, true)
        assertEquals(MicMode.NONE, speaking.mic)
        assertEquals(OutputIntent.SPEECH, speaking.output)
        assertEquals(MicMode.SPEECH, playing.mic)
        assertEquals(OutputIntent.TONES, playing.output)
    }

    @Test
    fun idleListeningFollowsTheSwitch() {
        val on = AudioTurnPolicy.plan(Phase.IDLE, null, keepListening = true)
        val off = AudioTurnPolicy.plan(Phase.IDLE, null, keepListening = false)
        assertEquals(MicMode.SPEECH, on.mic)
        assertEquals(FocusRequest.NONE, on.focus)
        assertEquals(MicMode.NONE, off.mic)
    }

    private fun goertzel(samples: ShortArray, sampleRate: Int, frequency: Double): Double {
        val n = samples.size
        val k = (0.5 + n * frequency / sampleRate).toInt()
        val omega = 2.0 * Math.PI * k / n
        val coeff = 2.0 * kotlin.math.cos(omega)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (sample in samples) {
            s0 = sample / 32768.0 + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}
