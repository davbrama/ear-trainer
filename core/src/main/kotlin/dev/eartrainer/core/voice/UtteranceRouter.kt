package dev.eartrainer.core.voice

import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.Phase
import dev.eartrainer.core.model.VoiceAction

data class RouteContext(
    val phase: Phase,
    val wakeRequired: Boolean,
)

sealed interface RouteDecision {
    val confidence: Float

    data class Command(
        val action: VoiceAction,
        override val confidence: Float,
    ) : RouteDecision

    data class Answer(
        val interval: Interval,
        override val confidence: Float,
    ) : RouteDecision

    data class Unclear(
        val utterance: String,
        override val confidence: Float,
    ) : RouteDecision

    data object Ignore : RouteDecision {
        override val confidence: Float = 0f
    }
}

/**
 * Answers and commands share one microphone. Global commands always win.
 * Interval names count only while a question is open. "Yes" counts only while
 * a destructive confirmation or a feedback save is open.
 */
object UtteranceRouter {
    const val ANSWER_ACCEPT = 0.78f
    const val COMMAND_ACCEPT = 0.80f
    const val GLOBAL_INTERRUPT = 0.84f

    fun route(raw: String, context: RouteContext): RouteDecision {
        val hypotheses = listOf(raw)
        return choose(hypotheses.map { dev.eartrainer.core.port.SpeechHypothesis(it, 1f) }, context)
    }

    fun choose(
        hypotheses: List<dev.eartrainer.core.port.SpeechHypothesis>,
        context: RouteContext,
    ): RouteDecision {
        if (hypotheses.isEmpty()) return RouteDecision.Ignore
        val ranked = hypotheses.map { routeOne(it.text, context) }
        return ranked.maxBy { score(it) }
    }

    private fun score(decision: RouteDecision): Float = when (decision) {
        is RouteDecision.Command -> decision.confidence + 0.02f
        is RouteDecision.Answer -> decision.confidence
        is RouteDecision.Unclear -> 0.05f
        RouteDecision.Ignore -> 0f
    }

    private fun routeOne(raw: String, context: RouteContext): RouteDecision {
        val normalized = TextNormalizer.normalize(raw)
        if (normalized.isBlank()) return RouteDecision.Ignore
        val (stripped, woke) = TextNormalizer.stripWake(normalized)
        val text = TextNormalizer.stripFillers(stripped)
        val idleLike = context.phase == Phase.IDLE || context.phase == Phase.PAUSED || context.phase == Phase.PLAYING
        if (context.wakeRequired && !woke && idleLike) return RouteDecision.Ignore
        if (text.isBlank()) return RouteDecision.Ignore

        val command = IntentParser.parse(text)
        val answer = AnswerParser.parse(text)

        if (context.phase == Phase.AWAITING_CONFIRM) {
            if (command?.action is VoiceAction.Confirm || command?.action is VoiceAction.Cancel) {
                return RouteDecision.Command(command.action, command.confidence)
            }
            if (command != null && command.priority == CommandPriority.GLOBAL && command.action !is VoiceAction.StartSession) {
                return RouteDecision.Command(command.action, command.confidence)
            }
            return RouteDecision.Unclear(text, 0.2f)
        }

        if (context.phase == Phase.AWAITING_FEEDBACK) {
            if (command?.action is VoiceAction.SaveFeedback ||
                command?.action is VoiceAction.Confirm ||
                command?.action is VoiceAction.Cancel
            ) {
                return RouteDecision.Command(command.action, command.confidence)
            }
            if (command != null && command.priority == CommandPriority.GLOBAL &&
                command.action !is VoiceAction.StartSession
            ) {
                return RouteDecision.Command(command.action, command.confidence)
            }
            return RouteDecision.Command(VoiceAction.CaptureFeedback(text), 0.74f)
        }

        if (context.phase != Phase.AWAITING_ANSWER) {
            if (command != null &&
                command.priority != CommandPriority.PHASE_ONLY &&
                command.confidence >= COMMAND_ACCEPT
            ) {
                return RouteDecision.Command(command.action, command.confidence)
            }
            return RouteDecision.Ignore
        }

        val interrupt = command
        if (interrupt != null &&
            interrupt.priority == CommandPriority.GLOBAL &&
            interrupt.confidence >= GLOBAL_INTERRUPT &&
            (answer == null || interrupt.confidence >= answer.confidence)
        ) {
            return RouteDecision.Command(interrupt.action, interrupt.confidence)
        }
        if (answer != null && answer.confidence >= ANSWER_ACCEPT) {
            return RouteDecision.Answer(answer.interval, answer.confidence)
        }
        if (command != null &&
            command.priority == CommandPriority.NORMAL &&
            command.confidence >= COMMAND_ACCEPT
        ) {
            return RouteDecision.Command(command.action, command.confidence)
        }
        return RouteDecision.Unclear(text, 0.3f)
    }
}
