package dev.eartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dev.eartrainer.core.audio.FocusRequest
import dev.eartrainer.core.port.AudioFocusPort

/**
 * The session holds media focus for the whole drill so the car radio does not
 * jump back in between two notes. Focus is released when the drill is paused or idle.
 *
 * A held request is kept across phases instead of being abandoned and re-requested.
 * Re-requesting on every phase would steal focus back from our own recognizer
 * while it is listening. Focus callbacks arrive later on the main looper, so the
 * caller decides which losses are ours (see [dev.eartrainer.core.audio.PauseArbiter]).
 */
class AndroidAudioFocus(
    context: Context,
    private val log: (String) -> Unit = {},
) : AudioFocusPort {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var held: FocusRequest = FocusRequest.NONE
    private var lostForGood = false
    private var onLost: (Boolean) -> Unit = {}
    private var generation = 0

    override fun apply(focus: FocusRequest) {
        if (focus == FocusRequest.NONE) {
            release()
            return
        }
        if (request != null && focus == held && !lostForGood) return
        release()
        val generation = ++this.generation
        val gain = if (focus == FocusRequest.HOLD_MEDIA) {
            AudioManager.AUDIOFOCUS_GAIN
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val next = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (generation == this.generation) onChange(change)
            }
            .build()
        request = next
        held = focus
        lostForGood = false
        val result = manager.requestAudioFocus(next)
        log("focus request ${focus.name} -> ${resultName(result)}")
    }

    override fun release() {
        val current = request ?: return
        request = null
        held = FocusRequest.NONE
        lostForGood = false
        generation += 1
        manager.abandonAudioFocusRequest(current)
        log("focus abandoned")
    }

    override fun setOnFocusLost(listener: (transient: Boolean) -> Unit) {
        onLost = listener
    }

    private fun onChange(change: Int) {
        log("focus change ${changeName(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                lostForGood = true
                onLost(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLost(true)
            else -> Unit
        }
    }

    private fun resultName(result: Int): String = when (result) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
        else -> "failed"
    }

    private fun changeName(change: Int): String = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> change.toString()
    }
}
