package dev.eartrainer.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.eartrainer.core.audio.ToneSynthesizer
import dev.eartrainer.core.audio.Wav
import dev.eartrainer.core.model.Exercise
import dev.eartrainer.core.model.Instrument
import java.io.File

/**
 * Renders an exercise to a short WAV and plays it on the media session's player.
 * ExoPlayer's own audio-focus handling is off; the session coordinator owns focus.
 */
class TonePlayer(
    private val player: ExoPlayer,
    private val cacheDir: File,
) {
    private val synth = ToneSynthesizer()
    private var gate = 0
    var onEnded: (() -> Unit)? = null
    var onUserPause: (() -> Unit)? = null
    var onUserPlay: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (gate > 0) return
                if (playbackState == Player.STATE_ENDED) onEnded?.invoke()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (gate > 0) return
                if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
                if (playWhenReady) onUserPlay?.invoke() else onUserPause?.invoke()
            }
        })
    }

    fun play(exercise: Exercise, instrument: Instrument, metadata: MediaMetadata) {
        val item = itemFor(write(exercise, instrument), metadata)
        within {
            onEnded = onEnded
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    fun write(exercise: Exercise, instrument: Instrument): File {
        cacheDir.mkdirs()
        val file = File(cacheDir, "interval.wav")
        file.writeBytes(Wav.encode(synth.render(exercise.notes, instrument), 44_100))
        return file
    }

    fun mediaItem(file: File, metadata: MediaMetadata): MediaItem = itemFor(file, metadata)

    /** Undo a controller pause the session decided to ignore. */
    fun resumeIfPaused() {
        if (player.playWhenReady || player.playbackState == Player.STATE_ENDED) return
        within { player.play() }
    }

    fun stop() {
        onEnded = null
        within {
            player.pause()
            player.stop()
        }
    }

    private fun itemFor(file: File, metadata: MediaMetadata): MediaItem = MediaItem.Builder()
        .setMediaId(NOW_PLAYING)
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(metadata)
        .build()

    private inline fun within(block: () -> Unit) {
        gate += 1
        try {
            block()
        } finally {
            gate -= 1
        }
    }

    companion object {
        const val NOW_PLAYING = "now_playing"
    }
}
