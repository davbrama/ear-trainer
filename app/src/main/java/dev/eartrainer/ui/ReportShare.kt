package dev.eartrainer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.eartrainer.CrashLog
import dev.eartrainer.PauseLog
import dev.eartrainer.core.model.ResolvedLanguage
import java.io.File

/** One shareable text file: the crash log, if any, followed by the pause log. */
object ReportShare {
    fun share(context: Context, language: ResolvedLanguage) {
        val app = context.applicationContext as Application
        val version = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val body = buildString {
            appendLine("Ear Trainer $version")
            appendLine()
            appendLine("==== Crash log ====")
            appendLine(CrashLog.read(app) ?: "(none)")
            appendLine()
            appendLine("==== Pause log (newest last) ====")
            appendLine(PauseLog.read(app) ?: "(none)")
        }
        val file = File(File(app.filesDir, "eartrainer"), "report.txt")
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ear Trainer report $version")
            putExtra(Intent.EXTRA_TEXT, body.takeLast(4000))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(share, Copy.crashShare(language))) }
    }
}
