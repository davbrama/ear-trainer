package dev.eartrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.eartrainer.CrashLog
import dev.eartrainer.core.model.ResolvedLanguage

@Composable
fun CrashReportBanner(language: ResolvedLanguage) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    var report by remember { mutableStateOf(CrashLog.read(app)) }
    val text = report ?: return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Copy.crashTitle(language), style = MaterialTheme.typography.titleMedium)
            Text(Copy.crashBody(language), style = MaterialTheme.typography.bodyMedium)
            Text(
                text.takeLast(1800),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ReportShare.share(context, language) }) { Text(Copy.crashShare(language)) }
                TextButton(onClick = {
                    CrashLog.clear(app)
                    report = null
                }) { Text(Copy.crashClear(language)) }
            }
        }
    }
}
