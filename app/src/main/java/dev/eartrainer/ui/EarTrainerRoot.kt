package dev.eartrainer.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.eartrainer.core.model.AppLanguage
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Instrument
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.ResolvedLanguage
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.model.resolve
import dev.eartrainer.core.voice.Prompts
import dev.eartrainer.service.UiState
import java.io.File

private enum class Tab { Practice, Progress, Notes, Settings }

@Composable
fun EarTrainerRoot(
    viewModel: MainViewModel,
    systemTag: String,
    micGranted: Boolean,
    bypassMic: Boolean,
    onRequestMic: () -> Unit,
    onBypassMic: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val language = state.session.settings.language.resolve(systemTag)
    val direction = if (language == ResolvedLanguage.HEBREW) LayoutDirection.Rtl else LayoutDirection.Ltr
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides direction) {
        dev.eartrainer.ui.theme.EarTrainerTheme {
            Column(Modifier.fillMaxSize()) {
                CrashReportBanner(language)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (!micGranted && !bypassMic) {
                        PermissionScreen(language, onRequestMic, onBypassMic)
                    } else {
                        Shell(state, language, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun Shell(state: UiState, language: ResolvedLanguage, viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Practice) }
    val context = LocalContext.current
    LaunchedEffect(state.exportPath) {
        val path = state.exportPath ?: return@LaunchedEffect
        val file = File(path)
        if (!file.exists()) return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ear Trainer feedback")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(share, Copy.export(language))) }
        viewModel.consumeExport()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Nav(Tab.Practice, Copy.practice(language), Icons.Outlined.GraphicEq, tab) { tab = it }
                Nav(Tab.Progress, Copy.progress(language), Icons.Outlined.BarChart, tab) { tab = it }
                Nav(Tab.Notes, Copy.notes(language), Icons.Outlined.ChatBubbleOutline, tab) { tab = it }
                Nav(Tab.Settings, Copy.settings(language), Icons.Outlined.Tune, tab) { tab = it }
            }
        },
    ) { padding ->
        if (!state.connected) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(Copy.connecting(language), style = MaterialTheme.typography.titleLarge)
            }
        } else {
            when (tab) {
                Tab.Practice -> PracticeScreen(state, language, viewModel::act, Modifier.padding(padding))
                Tab.Progress -> ProgressScreen(state, language, Modifier.padding(padding))
                Tab.Notes -> NotesScreen(state, language, viewModel::act, Modifier.padding(padding))
                Tab.Settings -> SettingsScreen(state, language, viewModel::act, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun RowScope.Nav(
    tab: Tab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = selected == tab,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

@Composable
private fun PermissionScreen(
    language: ResolvedLanguage,
    onAllow: () -> Unit,
    onBypass: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(Copy.eyebrow(language).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(Copy.micTitle(language), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(Copy.micBody(language), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(Copy.allowMic(language))
        }
        TextButton(onClick = onBypass, modifier = Modifier.fillMaxWidth()) {
            Text(Copy.buttonsOnly(language))
        }
    }
}

@Composable
private fun PracticeScreen(
    state: UiState,
    language: ResolvedLanguage,
    onAction: (VoiceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(Copy.eyebrow(language).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(Copy.phaseTitle(session.phase, language), style = MaterialTheme.typography.displaySmall)
        IntervalCompass(session.phase, session.exercise?.direction)
        session.banner?.let { banner ->
            Text(
                Prompts.render(banner.key, language, banner.args),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (session.showAnswer) {
            session.exercise?.trainedInterval?.let { interval ->
                Text(Prompts.intervalName(interval, language), style = MaterialTheme.typography.headlineMedium)
            }
        }
        session.lastTranscript?.takeIf { it.isNotBlank() }?.let { heard ->
            Text(Copy.heard(language, heard), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(Copy.hint(session.phase, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        if (session.phase == Phase.AWAITING_CONFIRM) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAction(VoiceAction.Confirm) }, modifier = Modifier.weight(1f)) {
                    Text(Copy.confirm(language))
                }
                OutlinedButton(onClick = { onAction(VoiceAction.Cancel) }, modifier = Modifier.weight(1f)) {
                    Text(Copy.cancel(language))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAction(VoiceAction.Repeat) }, modifier = Modifier.weight(1f)) { Text(Copy.repeat(language)) }
                OutlinedButton(onClick = { onAction(VoiceAction.Reveal) }, modifier = Modifier.weight(1f)) { Text(Copy.reveal(language)) }
                OutlinedButton(onClick = { onAction(VoiceAction.Skip) }, modifier = Modifier.weight(1f)) { Text(Copy.skip(language)) }
            }
            Spacer(Modifier.height(12.dp))
            val running = session.phase != Phase.IDLE && session.phase != Phase.PAUSED
            Button(
                onClick = {
                    onAction(
                        when (session.phase) {
                            Phase.IDLE -> VoiceAction.StartSession
                            Phase.PAUSED -> VoiceAction.Resume
                            else -> VoiceAction.Pause
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    when {
                        session.phase == Phase.PAUSED -> Copy.resume(language)
                        running -> Copy.pause(language)
                        else -> Copy.start(language)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProgressScreen(state: UiState, language: ResolvedLanguage, modifier: Modifier = Modifier) {
    val memories = state.session.memories
    val attempts = memories.values.sumOf { it.attempts }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(Copy.progress(language), style = MaterialTheme.typography.displaySmall)
        Text(Copy.sessionScore(language, state.session.correct, state.session.asked), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (attempts == 0) {
            Text(Copy.emptyStats(language), style = MaterialTheme.typography.bodyLarge)
        }
        Interval.entries.forEach { interval ->
            val memory = memories[interval]
            val ratio = if (memory == null || memory.attempts == 0) 0f else memory.accuracy.toFloat()
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Prompts.intervalName(interval, language))
                    Text(if (memory == null || memory.attempts == 0) "—" else "${memory.correct}/${memory.attempts}")
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(
    state: UiState,
    language: ResolvedLanguage,
    onAction: (VoiceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Copy.notes(language), style = MaterialTheme.typography.displaySmall)
        if (state.feedback.isEmpty()) {
            Text(Copy.emptyNotes(language), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.feedback.asReversed().forEach { entry ->
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
        }
        TextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(Copy.noteHint(language)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAction(VoiceAction.BeginFeedbackWithText(draft.trim()))
                        draft = ""
                    }
                },
            ) { Text(Copy.saveNote(language)) }
            OutlinedButton(onClick = { onAction(VoiceAction.ExportFeedback) }) { Text(Copy.export(language)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: UiState,
    language: ResolvedLanguage,
    onAction: (VoiceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.session.settings
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(Copy.settings(language), style = MaterialTheme.typography.displaySmall)
        Section(Copy.level(language)) {
            ChipRow {
                IntervalSet.entries.forEach { set ->
                    FilterChip(
                        selected = settings.intervalSet == set,
                        onClick = { onAction(VoiceAction.SetIntervalSet(set)) },
                        label = { Text(Copy.levelName(set, language)) },
                    )
                }
            }
        }
        Section(Copy.direction(language)) {
            ChipRow {
                DirectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.directionMode == mode,
                        onClick = { onAction(VoiceAction.SetDirectionMode(mode)) },
                        label = { Text(Copy.directionName(mode, language)) },
                    )
                }
            }
        }
        Section(Copy.instrument(language)) {
            ChipRow {
                Instrument.entries.forEach { instrument ->
                    FilterChip(
                        selected = settings.instrument == instrument,
                        onClick = { onAction(VoiceAction.SetInstrument(instrument)) },
                        label = { Text(Copy.instrumentName(instrument, language)) },
                    )
                }
            }
        }
        Section(Copy.language(language)) {
            ChipRow {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = settings.language == option,
                        onClick = { onAction(VoiceAction.SetLanguage(option)) },
                        label = { Text(Copy.languageName(option, language)) },
                    )
                }
            }
        }
        Section(Copy.tempo(language)) {
            var tempo by rememberSaveable(settings.tempo) { mutableIntStateOf((settings.tempo * 100).toInt()) }
            Text("$tempo%")
            Slider(
                value = tempo.toFloat(),
                onValueChange = { tempo = it.toInt() },
                onValueChangeFinished = { onAction(VoiceAction.SetTempo(tempo / 100f)) },
                valueRange = 50f..200f,
            )
        }
        Section(Copy.length(language)) {
            var minutes by rememberSaveable(settings.sessionMinutes) { mutableIntStateOf(settings.sessionMinutes) }
            Text(Copy.minutes(language, minutes))
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = it.toInt() },
                onValueChangeFinished = { onAction(VoiceAction.SetSessionLength(minutes)) },
                valueRange = 5f..60f,
            )
        }
        ToggleRow(Copy.adaptive(language), Copy.adaptiveHelp(language), settings.adaptive) {
            onAction(VoiceAction.SetAdaptive(it))
        }
        ToggleRow(Copy.wake(language), Copy.wakeHelp(language), settings.wakeRequired) {
            onAction(VoiceAction.SetWakeRequired(it))
        }
        ToggleRow(Copy.listening(language), null, settings.listenForCommands) {
            onAction(VoiceAction.SetListening(it))
        }
        OutlinedButton(onClick = { onAction(VoiceAction.RequestReset) }, modifier = Modifier.fillMaxWidth()) {
            Text(Copy.reset(language))
        }
        if (state.session.phase == Phase.AWAITING_CONFIRM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(VoiceAction.Confirm) }) { Text(Copy.confirm(language)) }
                TextButton(onClick = { onAction(VoiceAction.Cancel) }) { Text(Copy.cancel(language)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(title: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (help != null) {
                Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
