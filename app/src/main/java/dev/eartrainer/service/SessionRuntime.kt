package dev.eartrainer.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.eartrainer.audio.SwitchingAudioInput
import dev.eartrainer.audio.TonePlayer
import dev.eartrainer.core.audio.AudioTurnPolicy
import dev.eartrainer.core.audio.MicMode
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.ResolvedLanguage
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.model.resolve
import dev.eartrainer.core.port.AudioFocusPort
import dev.eartrainer.core.port.Coach
import dev.eartrainer.core.port.FeedbackChannel
import dev.eartrainer.core.port.SpeechHypothesis
import dev.eartrainer.core.port.SpeechSynthesizer
import dev.eartrainer.core.practice.Effect
import dev.eartrainer.core.practice.PracticeSession
import dev.eartrainer.core.practice.SessionSnapshot
import dev.eartrainer.core.voice.PromptKey
import dev.eartrainer.core.voice.Prompts
import dev.eartrainer.core.voice.RouteDecision
import dev.eartrainer.core.voice.UtteranceRouter
import dev.eartrainer.data.JsonStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val session: SessionSnapshot = SessionSnapshot.idle(),
    val feedbackCount: Int = 0,
    val feedback: List<dev.eartrainer.core.model.FeedbackEntry> = emptyList(),
    val exportPath: String? = null,
    val micGranted: Boolean = false,
    val connected: Boolean = false,
)

class SessionRuntime(
    context: Context,
    private val speechOut: SpeechSynthesizer,
    private val input: SwitchingAudioInput,
    private val focus: AudioFocusPort,
    private val player: TonePlayer,
    private val stores: JsonStores,
    private val coach: Coach,
    private val feedbackChannel: FeedbackChannel,
    private val scope: CoroutineScope,
    private val systemTag: () -> String,
    private val micGranted: () -> Boolean,
) {
    private val session = PracticeSession(
        settings = stores.loadSettings(),
        memories = stores.loadProgress(),
    )
    private val main = Handler(Looper.getMainLooper())
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eartrainer:practice")
    private val stateFlow = MutableStateFlow(currentState())
    private var speechToken = 0
    private var playToken = 0
    private var listenToken = 0
    private var hebrewWarned = false

    val state: StateFlow<UiState> = stateFlow.asStateFlow()

    init {
        focus.setOnFocusLost {
            scope.launch {
                val phase = session.snapshot().phase
                if (phase != Phase.IDLE && phase != Phase.PAUSED && phase != Phase.SPEAKING) {
                    dispatch(session.onAction(VoiceAction.Pause))
                }
            }
        }
        player.onUserPause = { submit(VoiceAction.Pause) }
        player.onUserPlay = {
            val phase = session.snapshot().phase
            when (phase) {
                Phase.PAUSED -> submit(VoiceAction.Resume)
                Phase.IDLE -> submit(VoiceAction.StartSession)
                else -> Unit
            }
        }
        holdWake()
        // Idle listening, so "start" works with the screen off after the service is up.
        openMic()
    }

    fun submit(action: VoiceAction) {
        scope.launch { dispatch(session.onAction(action)) }
    }

    fun refreshMic() {
        publish()
        if (micGranted()) openMic()
    }

    fun consumeExport() {
        publish(exportPath = null)
    }

    fun libraryItem(mediaId: String?, query: String?): MediaItem {
        val blob = listOfNotNull(mediaId, query).joinToString(" ").lowercase()
        val set = when {
            "advanced" in blob || "מתקדם" in blob -> IntervalSet.ADVANCED
            "intermediate" in blob || "בינוני" in blob -> IntervalSet.INTERMEDIATE
            "beginner" in blob || "מתחילים" in blob -> IntervalSet.BEGINNER
            else -> null
        }
        val direction = when {
            "ascend" in blob || "עולה" in blob -> DirectionMode.ASCENDING
            "descend" in blob || "יורד" in blob -> DirectionMode.DESCENDING
            "harmon" in blob || "הרמוני" in blob -> DirectionMode.HARMONIC
            "mixed" in blob || "מעורב" in blob -> DirectionMode.MIXED
            else -> null
        }
        dispatch(session.retune(set, direction))
        val play = session.startImmediate().filterIsInstance<Effect.Play>().first()
        val metadata = metadata(play.exercise.direction?.name)
        val file = player.write(play.exercise, session.snapshot().settings.instrument)
        val token = ++playToken
        player.onEnded = {
            scope.launch {
                if (token != playToken) return@launch
                playToken += 1
                dispatch(session.onPlaybackFinished())
            }
        }
        openMic()
        publish()
        return player.mediaItem(file, metadata)
    }

    fun metadata(detail: String? = null): MediaMetadata {
        val language = resolved()
        val phase = session.snapshot().phase
        val title = if (language == ResolvedLanguage.HEBREW) "תרגול מרווחים" else "Interval practice"
        val artist = detail ?: when (phase) {
            Phase.AWAITING_ANSWER -> if (language == ResolvedLanguage.HEBREW) "תורכם" else "Your turn"
            Phase.PLAYING -> if (language == ResolvedLanguage.HEBREW) "מנגן" else "Playing"
            Phase.PAUSED -> if (language == ResolvedLanguage.HEBREW) "מושהה" else "Paused"
            else -> if (language == ResolvedLanguage.HEBREW) "מאמן שמיעה" else "Ear Trainer"
        }
        return MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle("Ear Trainer").build()
    }

    fun release() {
        speechToken += 1
        playToken += 1
        listenToken += 1
        speechOut.stop()
        input.close()
        focus.release()
        player.stop()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun dispatch(effects: List<Effect>) {
        publish()
        for (effect in effects) {
            when (effect) {
                is Effect.PersistSettings -> stores.saveSettings(effect.settings)
                is Effect.PersistProgress -> stores.saveProgress(effect.memories)
                is Effect.PersistFeedback -> {
                    val all = stores.appendFeedback(effect.entry)
                    scope.launch { feedbackChannel.send(all) }
                    publish()
                }
                Effect.ExportFeedback -> {
                    val file = stores.exportFeedback(stores.loadFeedback())
                    publish(exportPath = file.absolutePath)
                }
                Effect.StopPlayback -> player.stop()
                Effect.ReleaseInput -> closeMic()
                is Effect.Speak -> {
                    speak(effect)
                    return
                }
                is Effect.Play -> {
                    play(effect)
                    return
                }
                is Effect.Listen -> {
                    listen(effect.delayMs)
                    return
                }
            }
        }
        publish()
    }

    private fun speak(effect: Effect.Speak) {
        val token = ++speechToken
        playToken += 1
        player.stop()
        closeMic()
        val language = resolved()
        val missingHebrew = language == ResolvedLanguage.HEBREW && !speechOut.isLanguageAvailable("he-IL")
        val text = if (missingHebrew) {
            val english = Prompts.render(effect.key, ResolvedLanguage.ENGLISH, effect.args)
            if (!hebrewWarned) {
                hebrewWarned = true
                english + " " + Prompts.render(PromptKey.HEBREW_TTS_MISSING, ResolvedLanguage.ENGLISH)
            } else {
                english
            }
        } else {
            Prompts.render(effect.key, language, effect.args)
        }
        applyPlan(Phase.SPEAKING)
        main.postDelayed({
            if (token == speechToken) finishSpeech(token)
        }, 12_000)
        speechOut.speak(
            text = text,
            languageTag = if (missingHebrew) "en-US" else language.tag(),
            onDone = { scope.launch { finishSpeech(token) } },
            onError = { scope.launch { finishSpeech(token) } },
        )
    }

    private fun finishSpeech(token: Int) {
        if (token != speechToken) return
        speechToken += 1
        dispatch(session.onSpeechFinished())
    }

    private fun play(effect: Effect.Play) {
        val token = ++playToken
        speechToken += 1
        speechOut.stop()
        closeMic()
        applyPlan(Phase.PLAYING)
        player.onEnded = {
            scope.launch {
                if (token != playToken) return@launch
                playToken += 1
                dispatch(session.onPlaybackFinished())
            }
        }
        player.play(effect.exercise, session.snapshot().settings.instrument, metadata())
        openMic()
    }

    private fun listen(delayMs: Long) {
        val token = ++listenToken
        main.postDelayed({
            if (token != listenToken) return@postDelayed
            openMic()
        }, delayMs)
    }

    private fun openMic() {
        if (!micGranted()) {
            input.close()
            return
        }
        val snap = session.snapshot()
        val plan = AudioTurnPolicy.plan(snap.phase, snap.exercise?.modality, snap.keepListening)
        focus.apply(plan.focus)
        input.use(
            mode = plan.mic,
            languageTag = resolved().tag(),
            onSpeech = { hypotheses -> scope.launch { onSpeech(hypotheses) } },
            onPitch = { estimate -> scope.launch { dispatch(session.onPitch(estimate)) } },
            onError = { message -> scope.launch { onMicError(message) } },
        )
    }

    private fun closeMic() {
        listenToken += 1
        input.close()
    }

    private fun onSpeech(hypotheses: List<SpeechHypothesis>) {
        val decision = UtteranceRouter.choose(hypotheses, session.routeContext())
        when (decision) {
            RouteDecision.Ignore -> openMic()
            is RouteDecision.Unclear -> scope.launch {
                val coached = coach.interpret(decision.utterance, session.coachContext(resolved().tag()))
                if (coached != null) dispatch(session.onAction(coached))
                else dispatch(session.accept(decision, decision.utterance))
            }
            else -> dispatch(session.accept(decision, hypotheses.firstOrNull()?.text.orEmpty()))
        }
    }

    private fun onMicError(message: String) {
        if (message != "no-match" && message != "timeout") return
        val snap = session.snapshot()
        val plan = AudioTurnPolicy.plan(snap.phase, snap.exercise?.modality, snap.keepListening)
        if (plan.mic == MicMode.SPEECH) openMic()
    }

    private fun applyPlan(phase: Phase) {
        val snap = session.snapshot()
        val plan = AudioTurnPolicy.plan(phase, snap.exercise?.modality, snap.keepListening)
        focus.apply(plan.focus)
    }

    private fun resolved(): ResolvedLanguage = session.snapshot().settings.language.resolve(systemTag())

    private fun holdWake() {
        if (!wakeLock.isHeld) wakeLock.acquire(2 * 60 * 60 * 1000L)
    }

    private fun currentState(exportPath: String? = null): UiState {
        val feedback = stores.loadFeedback()
        return UiState(
            session = session.snapshot(),
            feedbackCount = feedback.size,
            feedback = feedback,
            exportPath = exportPath,
            micGranted = micGranted(),
            connected = true,
        )
    }

    private fun publish(exportPath: String? = stateFlow.value.exportPath) {
        stateFlow.value = currentState(exportPath)
    }
}
