package dev.eartrainer.core.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

object Pitch {
    fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun centsBetween(heardHz: Double, targetHz: Double): Double {
        if (heardHz <= 0.0 || targetHz <= 0.0) return Double.POSITIVE_INFINITY
        return 1200.0 * (ln(heardHz / targetHz) / LN2)
    }

    fun nearestMidi(frequencyHz: Double): Int {
        if (frequencyHz <= 0.0) return 0
        val midi = 69.0 + 12.0 * (ln(frequencyHz / 440.0) / LN2)
        return round(midi).toInt()
    }

    private val LN2 = ln(2.0)
}

data class PitchEstimate(
    val frequencyHz: Double,
    val confidence: Float,
) {
    val midi: Int get() = Pitch.nearestMidi(frequencyHz)
    val centsOff: Double get() = Pitch.centsBetween(frequencyHz, Pitch.midiToHz(midi))
}

/**
 * Pitch tracker used by sing-back lessons. v1 ships [StubPitchDetector].
 * A YIN or pYIN implementation can replace it without changing the session.
 */
interface PitchDetector {
    val name: String

    fun detect(samples: ShortArray, sampleRate: Int): PitchEstimate?
}

class StubPitchDetector : PitchDetector {
    override val name: String = "stub"

    override fun detect(samples: ShortArray, sampleRate: Int): PitchEstimate? = null
}

object SingbackGrader {
    fun grade(
        expectedMidi: Int,
        estimate: PitchEstimate?,
        toleranceCents: Double = 50.0,
    ): Boolean {
        if (estimate == null || estimate.confidence < 0.5f) return false
        val cents = abs(Pitch.centsBetween(estimate.frequencyHz, Pitch.midiToHz(expectedMidi)))
        return cents <= toleranceCents
    }
}
