package dev.eartrainer.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.eartrainer.core.port.PcmCapture

/** Raw mic frames for a future pitch tracker. Not used by interval naming. */
class PcmMicCapture : PcmCapture {
    private var recorder: AudioRecord? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    override fun start(
        sampleRate: Int,
        onBuffer: (ShortArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val min = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) {
            onError("mic-buffer")
            return
        }
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            onError("mic-init")
            return
        }
        recorder = audio
        running = true
        audio.startRecording()
        Thread({
            val buffer = ShortArray(min)
            while (running) {
                val read = audio.read(buffer, 0, buffer.size)
                if (read > 0) onBuffer(buffer.copyOf(read))
            }
        }, "ear-trainer-pcm").start()
    }

    override fun stop() {
        running = false
        val audio = recorder
        recorder = null
        runCatching { audio?.stop() }
        audio?.release()
    }
}
