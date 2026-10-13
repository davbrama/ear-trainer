package dev.eartrainer.audio

import dev.eartrainer.core.audio.MicMode
import dev.eartrainer.core.audio.PitchDetector
import dev.eartrainer.core.audio.PitchEstimate
import dev.eartrainer.core.port.PcmCapture
import dev.eartrainer.core.port.SpeechHypothesis
import dev.eartrainer.core.port.SpeechTranscriber

/**
 * One microphone, two consumers. Speech recognition and pitch capture never run together.
 */
class SwitchingAudioInput(
    private val speech: SpeechTranscriber,
    private val pcm: PcmCapture,
    private val pitchDetector: PitchDetector,
) {
    private var mode: MicMode = MicMode.NONE

    fun use(
        mode: MicMode,
        languageTag: String,
        onSpeech: (List<SpeechHypothesis>) -> Unit,
        onPitch: (PitchEstimate) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (mode != MicMode.SPEECH) speech.stop()
        if (mode != MicMode.PITCH_PCM) pcm.stop()
        this.mode = mode
        when (mode) {
            MicMode.NONE -> Unit
            MicMode.SPEECH -> speech.start(languageTag, onSpeech, onError)
            MicMode.PITCH_PCM -> pcm.start(
                sampleRate = 44_100,
                onBuffer = { buffer ->
                    pitchDetector.detect(buffer, 44_100)?.let(onPitch)
                },
                onError = onError,
            )
        }
    }

    fun close() {
        speech.stop()
        pcm.stop()
        mode = MicMode.NONE
    }
}
