package dev.eartrainer

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records every pause request, which path sent it, and whether it paused the drill.
 * Also records audio-focus and recognizer events around it, so a shared report shows
 * what happened just before an unexpected pause. Kept in app storage and added to
 * the "Share report" text next to the crash log.
 */
object PauseLog {
    private const val TAG = "EarTrainerPause"
    private const val FILE_NAME = "pause.log"
    private const val MAX_BYTES = 64_000
    private var file: File? = null

    fun init(context: Context) {
        file = File(File(context.filesDir, "eartrainer"), FILE_NAME)
    }

    fun file(context: Context): File = File(File(context.filesDir, "eartrainer"), FILE_NAME)

    @Synchronized
    fun log(line: String) {
        Log.i(TAG, line)
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            if (target.length() > MAX_BYTES) {
                val tail = target.readText().takeLast(MAX_BYTES / 2)
                target.writeText(tail.substringAfter('\n'))
            }
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            target.appendText("$stamp $line\n")
        }
    }

    fun read(context: Context): String? {
        val target = file(context)
        if (!target.exists() || target.length() == 0L) return null
        return runCatching { target.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
