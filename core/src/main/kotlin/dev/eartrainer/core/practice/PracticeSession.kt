package dev.eartrainer.core.practice

import dev.eartrainer.core.audio.PitchEstimate
import dev.eartrainer.core.model.DirectionMode
import dev.eartrainer.core.model.Exercise
import dev.eartrainer.core.model.ExpectedAnswer
import dev.eartrainer.core.model.IntervalSet
import dev.eartrainer.core.model.FeedbackEntry
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalMemory
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.PracticeSettings
import dev.eartrainer.core.model.VoiceAction
import dev.eartrainer.core.port.CoachContext
import dev.eartrainer.core.voice.PromptKey
import dev.eartrainer.core.voice.RouteContext
import dev.eartrainer.core.voice.RouteDecision
import kotlin.random.Random

data class Banner(
    val key: PromptKey,
    val args: Map<String, String> = emptyMap(),
)

data class SessionSnapshot(
    val phase: Phase,
    val settings: PracticeSettings,
    val exercise: Exercise?,
    val showAnswer: Boolean,
    val banner: Banner?,
    val asked: Int,
    val correct: Int,
    val skipped: Int,
    val memories: Map<Interval, IntervalMemory>,
    val lastTranscript: String?,
    val keepListening: Boolean,
) {
    companion object {
        fun idle(settings: PracticeSettings = PracticeSettings()) = SessionSnapshot(
            phase = Phase.IDLE,
            settings = settings.normalized(),
            exercise = null,
            showAnswer = false,
            banner = null,
            asked = 0,
            correct = 0,
            skipped = 0,
            memories = emptyMap(),
            lastTranscript = null,
            keepListening = settings.listenForCommands,
        )
    }
}

sealed interface Effect {
    data class Speak(val key: PromptKey, val args: Map<String, String> = emptyMap()) : Effect
    data class Play(val exercise: Exercise) : Effect
    data class Listen(val delayMs: Long = 280) : Effect
    data object StopPlayback : Effect
    data object ReleaseInput : Effect
    data class PersistSettings(val settings: PracticeSettings) : Effect
    data class PersistProgress(val memories: Map<Interval, IntervalMemory>) : Effect
    data class PersistFeedback(val entry: FeedbackEntry) : Effect
    data object ExportFeedback : Effect
}

/**
 * Hands-free practice loop. The Android service only plays, speaks, and listens;
 * every transition lives here so it can be unit tested without a device.
 */
class PracticeSession(
    settings: PracticeSettings = PracticeSettings(),
    memories: Map<Interval, IntervalMemory> = emptyMap(),
    private val scheduler: AdaptiveScheduler = AdaptiveScheduler(),
    private val random: Random = Random.Default,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var settings: PracticeSettings = settings.normalized()
    private val memories = memories.toMutableMap()
    private var phase: Phase = Phase.IDLE
    private var exercise: Exercise? = null
    private var asked: Int = 0
    private var correctCount: Int = 0
    private var skipped: Int = 0
    private var startedAt: Long? = null
    private var showAnswer: Boolean = false
    private var banner: Banner? = null
    private var lastTranscript: String? = null
    private var feedbackDraft: String? = null
    private var resumePhase: Phase = Phase.IDLE
    private var phaseBeforeFeedback: Phase = Phase.IDLE
    private var afterSpeech: Next = Next.NONE
    private var afterPlay: Next = Next.NONE
    private var ids: Int = 1

    private enum class Next {
        NONE,
        PLAY_NEW,
        LISTEN_ANSWER,
        LISTEN_CONFIRM,
        LISTEN_FEEDBACK,
        REPLAY,
        RETURN,
        IDLE_AFTER_SUMMARY,
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        phase = phase,
        settings = settings,
        exercise = exercise,
        showAnswer = showAnswer,
        banner = banner,
        asked = asked,
        correct = correctCount,
        skipped = skipped,
        memories = memories.toMap(),
        lastTranscript = lastTranscript,
        keepListening = settings.listenForCommands,
    )

    fun routeContext(): RouteContext = RouteContext(phase, settings.wakeRequired)

    fun coachContext(languageTag: String): CoachContext = CoachContext(
        languageTag = languageTag,
        phase = phase.name,
        exerciseType = exercise?.type?.name,
    )

    fun accept(decision: RouteDecision, transcript: String): List<Effect> {
        lastTranscript = transcript
        return when (decision) {
            is RouteDecision.Ignore -> emptyList()
            is RouteDecision.Command -> onAction(decision.action)
            is RouteDecision.Answer -> onSpoken(decision.interval)
            is RouteDecision.Unclear -> onUnrecognized()
        }
    }

    fun onAction(action: VoiceAction): List<Effect> = when (action) {
        VoiceAction.StartSession -> when {
            phase == Phase.PAUSED && exercise != null -> resume()
            else -> startFresh()
        }
        VoiceAction.Pause -> pause()
        VoiceAction.Resume -> resume()
        VoiceAction.StopSession -> stop()
        VoiceAction.Repeat -> repeat()
        VoiceAction.Slower -> adjustTempo(1 / 1.12f)
        VoiceAction.Faster -> adjustTempo(1.12f)
        VoiceAction.Next, VoiceAction.Skip -> skip()
        VoiceAction.Reveal -> reveal()
        is VoiceAction.SetDirectionMode -> applySetting(
            settings.copy(directionMode = action.mode),
            PromptKey.DIRECTION_SET,
            mapOf("direction" to action.mode.name),
            rebuildAudio = true,
        )
        is VoiceAction.SetIntervalSet -> applySetting(
            settings.copy(intervalSet = action.set),
            PromptKey.LEVEL_SET,
            mapOf("level" to action.set.name),
        )
        is VoiceAction.AdjustDifficulty -> adjustDifficulty(action.delta)
        is VoiceAction.SetInstrument -> applySetting(
            settings.copy(instrument = action.instrument),
            PromptKey.INSTRUMENT_SET,
            mapOf("instrument" to action.instrument.name),
            rebuildAudio = true,
        )
        is VoiceAction.SetLanguage -> applySetting(
            settings.copy(language = action.language),
            PromptKey.LANGUAGE_SET,
            mapOf("language" to action.language.name),
        )
        is VoiceAction.SetTempo -> applySetting(
            settings.copy(tempo = action.speed),
            PromptKey.TEMPO_SET,
            mapOf("value" to percent(action.speed)),
            rebuildAudio = true,
        )
        is VoiceAction.SetSessionLength -> applySetting(
            settings.copy(sessionMinutes = action.minutes),
            PromptKey.LENGTH_SET,
            mapOf("value" to action.minutes.coerceIn(1, 120).toString()),
        )
        is VoiceAction.SetWakeRequired -> applySetting(
            settings.copy(wakeRequired = action.required),
            if (action.required) PromptKey.WAKE_ON else PromptKey.WAKE_OFF,
        )
        is VoiceAction.SetAdaptive -> applySetting(
            settings.copy(adaptive = action.enabled),
            if (action.enabled) PromptKey.ADAPTIVE_ON else PromptKey.ADAPTIVE_OFF,
        )
        is VoiceAction.SetListening -> applySetting(
            settings.copy(listenForCommands = action.enabled),
            if (action.enabled) PromptKey.LISTENING_ON else PromptKey.LISTENING_OFF,
        )
        VoiceAction.RequestReset -> requestReset()
        VoiceAction.Confirm -> confirm()
        VoiceAction.Cancel -> cancel()
        VoiceAction.Stats -> stats()
        VoiceAction.Help -> speakAside(PromptKey.HELP)
        VoiceAction.BeginFeedback -> beginFeedback()
        is VoiceAction.BeginFeedbackWithText -> saveFeedbackText(action.text)
        is VoiceAction.CaptureFeedback -> captureFeedback(action.text)
        VoiceAction.SaveFeedback -> saveDraft()
        VoiceAction.ExportFeedback -> speakAside(PromptKey.EXPORT_READY, extra = listOf(Effect.ExportFeedback))
    }

    fun onSpoken(interval: Interval): List<Effect> {
        if (phase != Phase.AWAITING_ANSWER) return speakAside(PromptKey.NEED_START)
        val exercise = exercise ?: return speakAside(PromptKey.NEED_START)
        val grade = AnswerGrader.grade(exercise, UserResponse.Speech(interval))
        return applySpokenGrade(grade.correct, grade.interval, revealKey = false)
    }

    fun onPitch(estimate: PitchEstimate?): List<Effect> {
        if (estimate == null || estimate.confidence < 0.5f) return emptyList()
        if (phase != Phase.AWAITING_ANSWER) return emptyList()
        val exercise = exercise ?: return emptyList()
        if (exercise.modality != dev.eartrainer.core.model.AnswerModality.SUNG_PITCH) return emptyList()
        val grade = AnswerGrader.grade(exercise, UserResponse.Sung(estimate))
        val sung = exercise.expected as? ExpectedAnswer.SungPitch
        showAnswer = true
        val key = if (grade.correct) PromptKey.CORRECT else PromptKey.INCORRECT
        banner = Banner(key, emptyMap())
        afterSpeech = Next.PLAY_NEW
        phase = Phase.SPEAKING
        if (grade.correct) correctCount += 1
        val args = if (sung != null) mapOf("interval" to "midi ${sung.midi}") else emptyMap()
        return listOf(Effect.ReleaseInput, Effect.Speak(key, args))
    }

    fun onSpeechFinished(): List<Effect> {
        val next = afterSpeech
        afterSpeech = Next.NONE
        return when (next) {
            Next.NONE -> emptyList()
            Next.PLAY_NEW -> playNew()
            Next.REPLAY -> replayThenAdvance()
            Next.LISTEN_CONFIRM -> {
                phase = Phase.AWAITING_CONFIRM
                listOf(Effect.Listen())
            }
            Next.LISTEN_FEEDBACK -> {
                phase = Phase.AWAITING_FEEDBACK
                listOf(Effect.Listen())
            }
            Next.RETURN -> returnTo(resumePhase)
            Next.IDLE_AFTER_SUMMARY -> {
                phase = Phase.IDLE
                if (settings.listenForCommands) listOf(Effect.Listen()) else listOf(Effect.ReleaseInput)
            }
            Next.LISTEN_ANSWER -> {
                phase = Phase.AWAITING_ANSWER
                listOf(Effect.Listen())
            }
        }
    }

    fun onPlaybackFinished(): List<Effect> {
        val next = afterPlay
        afterPlay = Next.NONE
        return when (next) {
            Next.LISTEN_ANSWER -> {
                phase = Phase.AWAITING_ANSWER
                listOf(Effect.Listen())
            }
            Next.PLAY_NEW -> playNew()
            else -> emptyList()
        }
    }

    /** Car browse and assistant search start on the first interval, without the spoken intro. */
    fun startImmediate(): List<Effect> {
        asked = 0
        correctCount = 0
        skipped = 0
        startedAt = clock()
        exercise = null
        showAnswer = false
        banner = null
        feedbackDraft = null
        return playNew()
    }

    fun retune(set: IntervalSet?, direction: DirectionMode?): List<Effect> {
        var updated = settings
        if (set != null) updated = updated.copy(intervalSet = set)
        if (direction != null) updated = updated.copy(directionMode = direction)
        if (updated == settings) return emptyList()
        settings = updated.normalized()
        return listOf(Effect.PersistSettings(settings))
    }

    fun onUnrecognized(): List<Effect> = when (phase) {
        Phase.AWAITING_ANSWER -> speakThen(PromptKey.DIDNT_CATCH, Next.LISTEN_ANSWER)
        Phase.AWAITING_CONFIRM -> speakThen(PromptKey.CONFIRM_RESET, Next.LISTEN_CONFIRM)
        Phase.AWAITING_FEEDBACK -> speakThen(PromptKey.FEEDBACK_GOT, Next.LISTEN_FEEDBACK)
        else -> emptyList()
    }

    /** Test hook: sing-back is not dealt in v1, but the grade path is live. */
    internal fun awaitSingbackForTest(exercise: Exercise) {
        this.exercise = exercise
        phase = Phase.AWAITING_ANSWER
        startedAt = clock()
        asked = 1
        showAnswer = false
    }

    private fun startFresh(): List<Effect> {
        asked = 0
        correctCount = 0
        skipped = 0
        startedAt = clock()
        exercise = null
        showAnswer = false
        banner = null
        feedbackDraft = null
        return speakThen(PromptKey.SESSION_START, Next.PLAY_NEW, listOf(Effect.StopPlayback, Effect.ReleaseInput))
    }

    private fun playNew(): List<Effect> {
        if (timeUp()) return finish()
        val pool = settings.intervalSet.intervals.toList().sortedBy { it.semitones }
        val interval = if (settings.adaptive) {
            scheduler.pick(pool, memories, clock(), random, avoid = exercise?.trainedInterval)
        } else {
            pool[random.nextInt(pool.size)]
        }
        val direction = IntervalExercises.chooseDirection(settings.directionMode, random)
        val root = IntervalExercises.pickRoot(interval, direction, random)
        exercise = IntervalExercises.naming(interval, direction, root, settings.tempo, "ex-${ids++}")
        asked += 1
        showAnswer = false
        banner = null
        phase = Phase.PLAYING
        afterPlay = Next.LISTEN_ANSWER
        return listOf(Effect.Play(exercise!!))
    }

    private fun replayThenAdvance(): List<Effect> {
        val current = exercise ?: return playNew()
        phase = Phase.PLAYING
        afterPlay = Next.PLAY_NEW
        showAnswer = true
        return listOf(Effect.Play(current))
    }

    private fun finish(): List<Effect> {
        val correctNow = correctCount
        val askedNow = asked
        phase = Phase.SPEAKING
        afterSpeech = Next.IDLE_AFTER_SUMMARY
        exercise = null
        showAnswer = false
        startedAt = null
        banner = Banner(PromptKey.SESSION_DONE, mapOf("correct" to "$correctNow", "asked" to "$askedNow"))
        return listOf(
            Effect.StopPlayback,
            Effect.ReleaseInput,
            Effect.Speak(PromptKey.SESSION_DONE, mapOf("correct" to "$correctNow", "asked" to "$askedNow")),
        )
    }

    private fun timeUp(): Boolean {
        val start = startedAt ?: return false
        if (asked == 0) return false
        return clock() - start >= settings.sessionMinutes * 60_000L
    }

    private fun applySpokenGrade(correct: Boolean, interval: Interval?, revealKey: Boolean): List<Effect> {
        if (interval != null) {
            val previous = memories[interval] ?: IntervalMemory()
            memories[interval] = scheduler.review(previous, correct = correct && !revealKey, nowMs = clock())
        }
        if (correct && !revealKey) correctCount += 1
        showAnswer = true
        val key = when {
            revealKey -> PromptKey.REVEAL
            correct -> PromptKey.CORRECT
            else -> PromptKey.INCORRECT
        }
        val args = interval?.let { mapOf("interval" to it.name) } ?: emptyMap()
        banner = Banner(key, args)
        val next = if (correct && !revealKey) Next.PLAY_NEW else Next.REPLAY
        return speakThen(
            key,
            next,
            listOf(Effect.ReleaseInput, Effect.PersistProgress(memories.toMap())),
            args,
        )
    }

    private fun reveal(): List<Effect> {
        val interval = exercise?.trainedInterval ?: return speakAside(PromptKey.NEED_START)
        if (phase == Phase.IDLE) return speakAside(PromptKey.NEED_START)
        return applySpokenGrade(correct = false, interval = interval, revealKey = true)
    }

    private fun skip(): List<Effect> {
        if (exercise == null || phase == Phase.IDLE) return speakAside(PromptKey.NEED_START)
        skipped += 1
        return listOf(Effect.StopPlayback) + playNew()
    }

    private fun repeat(): List<Effect> {
        val current = exercise ?: return speakAside(PromptKey.NEED_START)
        phase = Phase.PLAYING
        afterPlay = Next.LISTEN_ANSWER
        return listOf(Effect.StopPlayback, Effect.ReleaseInput, Effect.Play(current))
    }

    private fun pause(): List<Effect> {
        if (phase == Phase.IDLE || phase == Phase.PAUSED) return emptyList()
        resumePhase = Phase.PAUSED
        phase = Phase.SPEAKING
        afterSpeech = Next.RETURN
        resumePhase = Phase.PAUSED
        return listOf(Effect.StopPlayback, Effect.ReleaseInput, Effect.Speak(PromptKey.PAUSED))
    }

    private fun resume(): List<Effect> {
        if (exercise == null) return startFresh()
        phase = Phase.SPEAKING
        afterSpeech = Next.RETURN
        resumePhase = Phase.PLAYING
        return listOf(Effect.Speak(PromptKey.RESUMED))
    }

    private fun stop(): List<Effect> {
        if (startedAt == null && phase == Phase.IDLE) return emptyList()
        if (phase == Phase.IDLE) return emptyList()
        return finish()
    }

    private fun requestReset(): List<Effect> {
        resumePhase = if (phase == Phase.SPEAKING) Phase.IDLE else phase
        return speakThen(
            PromptKey.CONFIRM_RESET,
            Next.LISTEN_CONFIRM,
            listOf(Effect.StopPlayback, Effect.ReleaseInput),
        )
    }

    private fun confirm(): List<Effect> = when (phase) {
        Phase.AWAITING_CONFIRM -> {
            memories.clear()
            exercise = null
            asked = 0
            correctCount = 0
            skipped = 0
            startedAt = null
            showAnswer = false
            banner = Banner(PromptKey.RESET_DONE)
            speakThen(PromptKey.RESET_DONE, Next.IDLE_AFTER_SUMMARY, listOf(Effect.PersistProgress(emptyMap())))
        }
        Phase.AWAITING_FEEDBACK -> saveDraft()
        else -> speakAside(PromptKey.NOTHING_TO_CONFIRM)
    }

    private fun cancel(): List<Effect> = when (phase) {
        Phase.AWAITING_CONFIRM -> {
            banner = Banner(PromptKey.RESET_CANCELLED)
            val back = resumePhase
            resumePhase = back
            speakThen(PromptKey.RESET_CANCELLED, Next.RETURN)
        }
        Phase.AWAITING_FEEDBACK -> {
            feedbackDraft = null
            val back = phaseBeforeFeedback
            resumePhase = back
            banner = Banner(PromptKey.RESET_CANCELLED)
            speakThen(PromptKey.RESET_CANCELLED, Next.RETURN)
        }
        else -> speakAside(PromptKey.NOTHING_TO_CONFIRM)
    }

    private fun beginFeedback(): List<Effect> {
        phaseBeforeFeedback = if (phase == Phase.SPEAKING || phase == Phase.PLAYING) {
            if (exercise != null) Phase.AWAITING_ANSWER else Phase.IDLE
        } else {
            phase
        }
        feedbackDraft = null
        return speakThen(PromptKey.FEEDBACK_ASK, Next.LISTEN_FEEDBACK, listOf(Effect.StopPlayback, Effect.ReleaseInput))
    }

    private fun captureFeedback(text: String): List<Effect> {
        if (phase != Phase.AWAITING_FEEDBACK) return saveFeedbackText(text)
        feedbackDraft = text.trim()
        return speakThen(PromptKey.FEEDBACK_GOT, Next.LISTEN_FEEDBACK)
    }

    private fun saveDraft(): List<Effect> {
        val text = feedbackDraft?.trim().orEmpty()
        if (text.isEmpty()) return speakThen(PromptKey.FEEDBACK_EMPTY, Next.LISTEN_FEEDBACK)
        return saveFeedbackText(text, returnTo = phaseBeforeFeedback)
    }

    private fun saveFeedbackText(text: String, returnTo: Phase? = null): List<Effect> {
        val clean = text.trim()
        if (clean.isEmpty()) return speakAside(PromptKey.FEEDBACK_EMPTY)
        val entry = FeedbackEntry(
            id = "fb-${ids++}",
            createdAtEpochMs = clock(),
            text = clean,
            languageTag = settings.language.name,
        )
        feedbackDraft = null
        banner = Banner(PromptKey.FEEDBACK_SAVED)
        val back = returnTo ?: phase
        resumePhase = if (back == Phase.AWAITING_FEEDBACK || back == Phase.SPEAKING) Phase.IDLE else back
        return speakThen(
            PromptKey.FEEDBACK_SAVED,
            Next.RETURN,
            listOf(Effect.PersistFeedback(entry)),
        )
    }

    private fun stats(): List<Effect> {
        val lifetimeAttempts = memories.values.sumOf { it.attempts }
        if (lifetimeAttempts == 0 && asked == 0) return speakAside(PromptKey.NO_STATS)
        val weakest = memories.entries
            .filter { it.value.attempts > 0 }
            .sortedBy { it.value.accuracy }
            .take(2)
            .joinToString(",") { it.key.name }
            .ifBlank { "none" }
        return speakAside(
            PromptKey.STATS,
            mapOf(
                "correct" to correctCount.toString(),
                "asked" to asked.toString(),
                "lifetimeCorrect" to memories.values.sumOf { it.correct }.toString(),
                "lifetimeAttempts" to lifetimeAttempts.toString(),
                "weakest" to weakest,
            ),
        )
    }

    private fun adjustDifficulty(delta: Int): List<Effect> {
        val order = listOf(
            dev.eartrainer.core.model.IntervalSet.BEGINNER,
            dev.eartrainer.core.model.IntervalSet.INTERMEDIATE,
            dev.eartrainer.core.model.IntervalSet.ADVANCED,
        )
        val index = order.indexOf(settings.intervalSet).coerceAtLeast(0)
        val next = (index + delta).coerceIn(0, order.lastIndex)
        if (next == index) {
            val key = if (delta < 0) PromptKey.ALREADY_EASIEST else PromptKey.ALREADY_HARDEST
            return speakAside(key)
        }
        return applySetting(
            settings.copy(intervalSet = order[next]),
            PromptKey.LEVEL_SET,
            mapOf("level" to order[next].name),
        )
    }

    private fun adjustTempo(factor: Float): List<Effect> {
        val speed = (settings.tempo * factor).coerceIn(0.5f, 2f)
        return applySetting(
            settings.copy(tempo = speed),
            PromptKey.TEMPO_SET,
            mapOf("value" to percent(speed)),
            rebuildAudio = true,
        )
    }

    private fun applySetting(
        updated: PracticeSettings,
        key: PromptKey,
        args: Map<String, String> = emptyMap(),
        rebuildAudio: Boolean = false,
    ): List<Effect> {
        settings = updated.normalized()
        if (rebuildAudio && exercise != null) {
            exercise = IntervalExercises.rebuild(exercise!!, settings, random)
        }
        return speakAside(key, args, extra = listOf(Effect.PersistSettings(settings)))
    }

    private fun speakAside(
        key: PromptKey,
        args: Map<String, String> = emptyMap(),
        extra: List<Effect> = emptyList(),
    ): List<Effect> {
        resumePhase = when (phase) {
            Phase.PLAYING, Phase.AWAITING_ANSWER -> if (exercise != null) Phase.PLAYING else Phase.IDLE
            Phase.PAUSED -> Phase.PAUSED
            Phase.AWAITING_CONFIRM -> Phase.AWAITING_CONFIRM
            Phase.AWAITING_FEEDBACK -> Phase.AWAITING_FEEDBACK
            else -> Phase.IDLE
        }
        return speakThen(key, Next.RETURN, extra, args)
    }

    private fun returnTo(target: Phase): List<Effect> = when (target) {
        Phase.PLAYING, Phase.AWAITING_ANSWER -> {
            val current = exercise
            if (current == null) {
                phase = Phase.IDLE
                if (settings.listenForCommands) listOf(Effect.Listen()) else listOf(Effect.ReleaseInput)
            } else {
                phase = Phase.PLAYING
                afterPlay = Next.LISTEN_ANSWER
                listOf(Effect.Play(current))
            }
        }
        Phase.AWAITING_CONFIRM -> {
            phase = Phase.AWAITING_CONFIRM
            listOf(Effect.Listen())
        }
        Phase.AWAITING_FEEDBACK -> {
            phase = Phase.AWAITING_FEEDBACK
            listOf(Effect.Listen())
        }
        Phase.PAUSED -> {
            phase = Phase.PAUSED
            if (settings.listenForCommands) listOf(Effect.Listen()) else listOf(Effect.ReleaseInput)
        }
        else -> {
            phase = Phase.IDLE
            if (settings.listenForCommands) listOf(Effect.Listen()) else listOf(Effect.ReleaseInput)
        }
    }

    private fun speakThen(
        key: PromptKey,
        next: Next,
        extra: List<Effect> = emptyList(),
        args: Map<String, String> = emptyMap(),
    ): List<Effect> {
        phase = Phase.SPEAKING
        afterSpeech = next
        return extra + Effect.Speak(key, args)
    }

    private fun percent(speed: Float): String = (speed.coerceIn(0.5f, 2f) * 100).toInt().toString()
}
