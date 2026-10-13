package dev.eartrainer.core.port

import dev.eartrainer.core.audio.FocusRequest
import dev.eartrainer.core.model.FeedbackEntry
import dev.eartrainer.core.model.VoiceAction

data class SpeechHypothesis(
    val text: String,
    val confidence: Float,
)

/**
 * Speech-to-text. v1 uses Android [android.speech.SpeechRecognizer].
 * A future on-device model would implement the same contract.
 */
interface SpeechTranscriber {
    fun start(
        languageTag: String,
        onResult: (List<SpeechHypothesis>) -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()
}

/** Text-to-speech. v1 uses Android [android.speech.tts.TextToSpeech]. */
interface SpeechSynthesizer {
    fun speak(
        text: String,
        languageTag: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()

    fun isLanguageAvailable(languageTag: String): Boolean
}

/**
 * Raw microphone PCM for pitch lessons. Must not run at the same time as [SpeechTranscriber].
 * v1 wires this to AudioRecord; sing-back is not started yet.
 */
interface PcmCapture {
    fun start(
        sampleRate: Int,
        onBuffer: (ShortArray) -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()
}

interface AudioFocusPort {
    fun apply(focus: FocusRequest)
    fun release()

    /** [transient] is true for AUDIOFOCUS_LOSS_TRANSIENT, false for AUDIOFOCUS_LOSS. Ducking is not a loss. */
    fun setOnFocusLost(listener: (transient: Boolean) -> Unit)
}

/** Optional outbound path. v1 keeps feedback on the device and this returns failure. */
interface FeedbackChannel {
    val id: String

    suspend fun send(entries: List<FeedbackEntry>): Result<String>
}

class LocalOnlyFeedbackChannel : FeedbackChannel {
    override val id: String = "local-only"

    override suspend fun send(entries: List<FeedbackEntry>): Result<String> =
        Result.failure(IllegalStateException("No remote feedback channel is configured"))
}

interface Coach {
    val id: String

    /** Rules already failed or were ambiguous. Return null to keep the spoken fallback. */
    suspend fun interpret(utterance: String, context: CoachContext): VoiceAction?

    suspend fun critique(correct: Boolean, intervalName: String, context: CoachContext): String?

    suspend fun summarize(statsText: String, context: CoachContext): String?
}

data class CoachContext(
    val languageTag: String,
    val phase: String,
    val exerciseType: String?,
)

class LocalCoach : Coach {
    override val id: String = "local"

    override suspend fun interpret(utterance: String, context: CoachContext): VoiceAction? = null

    override suspend fun critique(correct: Boolean, intervalName: String, context: CoachContext): String? = null

    override suspend fun summarize(statsText: String, context: CoachContext): String? = null
}

/** Present so a later build can supply an API-backed coach without touching the session. */
class UnconfiguredLlmCoach : Coach {
    override val id: String = "llm-unconfigured"

    override suspend fun interpret(utterance: String, context: CoachContext): VoiceAction? = null

    override suspend fun critique(correct: Boolean, intervalName: String, context: CoachContext): String? = null

    override suspend fun summarize(statsText: String, context: CoachContext): String? = null
}

class Coaching(
    private val llm: Coach = UnconfiguredLlmCoach(),
    private val local: Coach = LocalCoach(),
) : Coach {
    override val id: String = "coaching"

    override suspend fun interpret(utterance: String, context: CoachContext): VoiceAction? =
        llm.interpret(utterance, context) ?: local.interpret(utterance, context)

    override suspend fun critique(correct: Boolean, intervalName: String, context: CoachContext): String? =
        llm.critique(correct, intervalName, context) ?: local.critique(correct, intervalName, context)

    override suspend fun summarize(statsText: String, context: CoachContext): String? =
        llm.summarize(statsText, context) ?: local.summarize(statsText, context)
}
