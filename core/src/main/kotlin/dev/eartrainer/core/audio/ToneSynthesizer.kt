package dev.eartrainer.core.audio

import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.NoteEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Small additive / Karplus-Strong synthesizer. No sample library.
 * The piano voice is a few decaying partials plus a short deterministic hammer.
 */
class ToneSynthesizer(
    private val sampleRate: Int = 44_100,
) {
    fun render(notes: List<NoteEvent>, instrument: Instrument): ShortArray {
        if (notes.isEmpty()) return ShortArray(0)
        val seconds = notes.maxOf { it.startSeconds + it.durationSeconds } + 0.06
        val length = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val mix = DoubleArray(length)
        for (note in notes) {
            renderNote(mix, note, instrument)
        }
        var peak = 1e-6
        for (sample in mix) peak = maxOf(peak, abs(sample))
        val scale = 0.82 * Short.MAX_VALUE / peak
        return ShortArray(length) { index ->
            (mix[index] * scale).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun renderNote(mix: DoubleArray, note: NoteEvent, instrument: Instrument) {
        val start = (note.startSeconds * sampleRate).toInt().coerceAtLeast(0)
        val count = (note.durationSeconds * sampleRate).toInt().coerceAtLeast(1)
        val freq = Pitch.midiToHz(note.midi)
        when (instrument) {
            Instrument.PLUCKED -> karplus(mix, start, count, freq, note.midi)
            else -> additive(mix, start, count, freq, note.durationSeconds, instrument)
        }
    }

    private fun additive(
        mix: DoubleArray,
        start: Int,
        count: Int,
        freq: Double,
        duration: Double,
        instrument: Instrument,
    ) {
        val partials = when (instrument) {
            Instrument.PIANO -> floatArrayOf(1f, 0.48f, 0.22f, 0.11f, 0.05f, 0.025f)
            Instrument.SOFT -> floatArrayOf(1f, 0.16f, 0.04f)
            Instrument.ORGAN -> floatArrayOf(1f, 0.55f, 0.18f, 0.35f)
            Instrument.PLUCKED -> floatArrayOf(1f)
        }
        for (i in 0 until count) {
            val index = start + i
            if (index !in mix.indices) break
            val t = i.toDouble() / sampleRate
            val attack = (t / 0.012).coerceIn(0.0, 1.0)
            val release = ((duration - t) / 0.04).coerceIn(0.0, 1.0)
            var sample = 0.0
            for (p in partials.indices) {
                val n = p + 1
                val stretch = if (instrument == Instrument.PIANO) 1.0 + 0.0008 * n else 1.0
                val decay = when (instrument) {
                    Instrument.ORGAN -> 1.0
                    Instrument.SOFT -> exp(-t * 0.9 / duration.coerceAtLeast(0.2))
                    else -> exp(-t * (1.5 + n * 0.7) / duration.coerceAtLeast(0.2))
                }
                val harmonic = if (instrument == Instrument.ORGAN && n == 4) freq * 1.5 else freq * n * stretch
                sample += partials[p] * decay * sin(2.0 * PI * harmonic * t)
            }
            if (instrument == Instrument.PIANO && t < 0.008) {
                sample += (1.0 - t / 0.008) * 0.18 * noise(i + start)
            }
            if (instrument == Instrument.ORGAN) {
                sample *= 1.0 + 0.045 * sin(2.0 * PI * 5.0 * t)
            }
            mix[index] += sample * attack * release * 0.35
        }
    }

    private fun karplus(mix: DoubleArray, start: Int, count: Int, freq: Double, midi: Int) {
        val period = (sampleRate / freq).toInt().coerceAtLeast(2)
        val buffer = DoubleArray(period) { noise(it + midi * 17) }
        var cursor = 0
        for (i in 0 until count) {
            val index = start + i
            if (index !in mix.indices) break
            val next = (cursor + 1) % period
            val averaged = 0.996 * 0.5 * (buffer[cursor] + buffer[next])
            buffer[cursor] = averaged
            val fade = if (i < 32) i / 32.0 else 1.0
            mix[index] += averaged * fade
            cursor = next
        }
    }

    private fun noise(seed: Int): Double {
        val x = (seed * 1103515245 + 12345).ushr(16)
        return ((x and 0xFFFF) / 32768.0) - 1.0
    }
}
