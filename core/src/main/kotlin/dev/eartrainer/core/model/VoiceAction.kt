package dev.eartrainer.core.model

/**
 * Every voice command and every on-screen control resolves to one of these.
 * An LLM coach, if added later, must return the same types rather than free text actions.
 */
sealed interface VoiceAction {
    data object StartSession : VoiceAction
    data object Pause : VoiceAction
    data object Resume : VoiceAction
    data object StopSession : VoiceAction
    data object Repeat : VoiceAction
    data object Slower : VoiceAction
    data object Faster : VoiceAction
    data object Next : VoiceAction
    data object Skip : VoiceAction
    data object Reveal : VoiceAction

    data class SetDirectionMode(val mode: DirectionMode) : VoiceAction
    data class SetIntervalSet(val set: IntervalSet) : VoiceAction
    data class AdjustDifficulty(val delta: Int) : VoiceAction
    data class SetInstrument(val instrument: Instrument) : VoiceAction
    data class SetLanguage(val language: AppLanguage) : VoiceAction
    data class SetTempo(val speed: Float) : VoiceAction
    data class SetSessionLength(val minutes: Int) : VoiceAction
    data class SetWakeRequired(val required: Boolean) : VoiceAction
    data class SetAdaptive(val enabled: Boolean) : VoiceAction
    data class SetListening(val enabled: Boolean) : VoiceAction

    data object RequestReset : VoiceAction
    data object Confirm : VoiceAction
    data object Cancel : VoiceAction
    data object Stats : VoiceAction
    data object Help : VoiceAction

    data object BeginFeedback : VoiceAction
    data class BeginFeedbackWithText(val text: String) : VoiceAction
    data class CaptureFeedback(val text: String) : VoiceAction
    data object SaveFeedback : VoiceAction
    data object ExportFeedback : VoiceAction
}
