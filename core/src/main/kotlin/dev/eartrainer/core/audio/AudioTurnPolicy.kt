package dev.eartrainer.core.audio

import dev.eartrainer.core.model.AnswerModality
import dev.eartrainer.core.model.Phase

enum class OutputIntent {
    NONE,
    TONES,
    SPEECH,
}

enum class MicMode {
    NONE,
    SPEECH,
    PITCH_PCM,
}

enum class FocusRequest {
    NONE,
    HOLD_MEDIA,
    TRANSIENT_DUCK,
}

data class AudioPlan(
    val output: OutputIntent,
    val mic: MicMode,
    val focus: FocusRequest,
)

/**
 * Decides who owns the speaker and the microphone.
 *
 * Speech output never shares the mic, or the recognizer would hear the prompts.
 * Pitch capture never overlaps playback, or it would grade our own tone.
 * During musical playback the speech mic stays open so "skip" and "stop" can barge in.
 * The router drops anything that is not a command in that phase.
 */
object AudioTurnPolicy {
    fun plan(
        phase: Phase,
        modality: AnswerModality?,
        keepListening: Boolean,
    ): AudioPlan {
        return when (phase) {
            Phase.PLAYING -> AudioPlan(OutputIntent.TONES, MicMode.SPEECH, FocusRequest.HOLD_MEDIA)
            Phase.SPEAKING -> AudioPlan(OutputIntent.SPEECH, MicMode.NONE, FocusRequest.HOLD_MEDIA)
            Phase.AWAITING_ANSWER -> AudioPlan(
                output = OutputIntent.NONE,
                mic = if (modality == AnswerModality.SUNG_PITCH) MicMode.PITCH_PCM else MicMode.SPEECH,
                focus = FocusRequest.HOLD_MEDIA,
            )
            Phase.AWAITING_CONFIRM,
            Phase.AWAITING_FEEDBACK,
            -> AudioPlan(OutputIntent.NONE, MicMode.SPEECH, FocusRequest.HOLD_MEDIA)
            Phase.PAUSED,
            Phase.IDLE,
            -> AudioPlan(
                output = OutputIntent.NONE,
                mic = if (keepListening) MicMode.SPEECH else MicMode.NONE,
                focus = FocusRequest.NONE,
            )
        }
    }

}
