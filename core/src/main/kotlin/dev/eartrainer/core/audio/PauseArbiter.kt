package dev.eartrainer.core.audio

import dev.eartrainer.core.model.Phase

/** Every way a pause request can reach the session. */
enum class PauseSource {
    /** The router heard "pause" / "hold on" / "השהה" / "רגע". */
    VOICE_COMMAND,

    /** The on-screen pause button. */
    APP_BUTTON,

    /** A MediaSession controller: notification, headset, Bluetooth head unit, Android Auto. */
    MEDIA_CONTROLLER,

    /** AUDIOFOCUS_LOSS. */
    FOCUS_LOSS,

    /** AUDIOFOCUS_LOSS_TRANSIENT. */
    FOCUS_LOSS_TRANSIENT,
    ;

    val explicit: Boolean get() = this == VOICE_COMMAND || this == APP_BUTTON
    val focus: Boolean get() = this == FOCUS_LOSS || this == FOCUS_LOSS_TRANSIENT
}

/** Our own audio clients that take audio focus away from our own media request. */
enum class InternalAudioClient {
    TTS,
    RECOGNIZER,
}

data class PauseDecision(
    val pause: Boolean,
    val source: PauseSource,
    val reason: String,
)

/**
 * Decides whether a pause request should pause the drill.
 *
 * Android does not say who took audio focus, so losses are attributed by time.
 * Each internal client has its own window; a late loss from one client never
 * uses up or closes the window of another.
 *
 * - TTS: from the start of a prompt until [ttsTailMs] after it ends.
 *   Some engines take focus while speaking and their loss can arrive after onDone.
 * - Recognizer: [recognizerLeadMs] after each startListening.
 *   The system recognizer takes transient focus to mute media and play its beep.
 *
 * Explicit requests (voice command, app button) always pause.
 * Non-explicit requests are ignored for [settleMs] after start or resume.
 */
class PauseArbiter(
    private val clock: () -> Long,
    private val settleMs: Long = 2_500,
    private val ttsTailMs: Long = 1_500,
    private val recognizerLeadMs: Long = 3_000,
) {
    private class ClientWindow(var startedAt: Long? = null, var endedAt: Long? = null)

    private val windows = InternalAudioClient.entries.associateWith { ClientWindow() }
    private var settledAt: Long? = null

    fun clientStarted(client: InternalAudioClient) {
        val window = windows.getValue(client)
        window.startedAt = clock()
        window.endedAt = null
    }

    fun clientStopped(client: InternalAudioClient) {
        val window = windows.getValue(client)
        if (window.startedAt != null && window.endedAt == null) window.endedAt = clock()
    }

    /** Call on start and resume. Opens the settle window for non-explicit pauses. */
    fun sessionStarted() {
        settledAt = clock() + settleMs
    }

    /** The internal client a focus loss right now would be blamed on, or null if it looks external. */
    fun attribute(): InternalAudioClient? {
        val now = clock()
        return InternalAudioClient.entries.firstOrNull { covers(it, now) }
    }

    fun decide(source: PauseSource, phase: Phase): PauseDecision {
        if (phase == Phase.IDLE || phase == Phase.PAUSED) {
            return PauseDecision(false, source, "not running (${phase.name})")
        }
        if (source.explicit) return PauseDecision(true, source, "explicit")
        if (source.focus) {
            val owner = attribute()
            if (owner != null) return PauseDecision(false, source, "own ${owner.name} focus change")
            if (phase == Phase.SPEAKING) return PauseDecision(false, source, "speaking")
        }
        val until = settledAt
        if (until != null && clock() < until) {
            return PauseDecision(false, source, "settling after start/resume (${until - clock()}ms left)")
        }
        return PauseDecision(true, source, if (source.focus) "external focus loss" else "media controller")
    }

    private fun covers(client: InternalAudioClient, now: Long): Boolean {
        val window = windows.getValue(client)
        val started = window.startedAt ?: return false
        if (now < started) return false
        return when (client) {
            InternalAudioClient.TTS -> {
                val ended = window.endedAt ?: return true
                now - ended <= ttsTailMs
            }
            InternalAudioClient.RECOGNIZER -> now - started <= recognizerLeadMs
        }
    }
}
