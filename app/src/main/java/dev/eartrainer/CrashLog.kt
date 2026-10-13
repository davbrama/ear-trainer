package dev.eartrainer

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to app storage before the process dies.
 * The next launch can show and share the file.
 */
object CrashLog {
    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 100_000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = file(app)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(app, file, thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun file(app: Application): File = File(File(app.filesDir, "eartrainer"), FILE_NAME)

    fun read(app: Application): String? {
        val file = file(app)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(app: Application) {
        runCatching { file(app).delete() }
    }

    private fun write(app: Application, file: File, thread: Thread, error: Throwable) {
        runCatching {
            file.parentFile?.mkdirs()
            if (file.length() > MAX_BYTES) {
                val tail = file.readBytes().takeLast(MAX_BYTES / 2).toByteArray()
                file.writeBytes(tail)
            }
            val version = runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: "?"
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val body = buildString {
                appendLine("---- $stamp  version=$version  thread=${thread.name} ----")
                appendLine(Log.getStackTraceString(error))
                appendLine()
            }
            FileOutputStream(file, true).use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
        }
    }
}
