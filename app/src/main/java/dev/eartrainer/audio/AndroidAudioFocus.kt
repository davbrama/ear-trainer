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
 */
class AndroidAudioFocus(
    context: Context,
) : AudioFocusPort {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var onLost: () -> Unit = {}
    private var applying = false

    override fun apply(focus: FocusRequest) {
        if (focus == FocusRequest.NONE) {
            release()
            return
        }
        release()
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
                if (applying) return@setOnAudioFocusChangeListener
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onLost()
                }
            }
            .build()
        applying = true
        request = next
        manager.requestAudioFocus(next)
        applying = false
    }

    override fun release() {
        applying = true
        request?.let { manager.abandonAudioFocusRequest(it) }
        request = null
        applying = false
    }

    override fun setOnFocusLost(listener: () -> Unit) {
        onLost = listener
    }
}
