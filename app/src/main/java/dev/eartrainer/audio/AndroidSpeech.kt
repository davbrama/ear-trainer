package dev.eartrainer.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.eartrainer.core.port.SpeechHypothesis
import dev.eartrainer.core.port.SpeechSynthesizer
import dev.eartrainer.core.port.SpeechTranscriber
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android's built-in recognizer. One utterance at a time; the session restarts it.
 * This is the v1 speech path. A later model would implement [SpeechTranscriber].
 */
class AndroidSpeechTranscriber(
    context: Context,
) : SpeechTranscriber {
    private val appContext = context.applicationContext
    // SpeechRecognizer throws if it is created or used off the main thread.
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    override fun start(
        languageTag: String,
        onResult: (List<SpeechHypothesis>) -> Unit,
        onError: (String) -> Unit,
    ) {
        handler.post {
            stopOnMain()
            val generation = ++this.generation
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                onError("unavailable")
                return@post
            }
            val speech = try {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } catch (error: RuntimeException) {
                onError("speech-init")
                return@post
            }
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    if (generation != this@AndroidSpeechTranscriber.generation) return
                    deliver(results, onResult)
                }

                override fun onPartialResults(partialResults: Bundle) = Unit

                override fun onError(error: Int) {
                    if (generation != this@AndroidSpeechTranscriber.generation) return
                    val code = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> "no-match"
                        else -> "speech-$error"
                    }
                    onError(code)
                }

                override fun onReadyForSpeech(params: Bundle) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle) = Unit
            })
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            try {
                speech.startListening(intent)
            } catch (error: RuntimeException) {
                stopOnMain()
                onError("speech-init")
            }
        }
    }

    override fun stop() {
        handler.post {
            generation += 1
            stopOnMain()
        }
    }

    fun release() {
        handler.post {
            generation += 1
            stopOnMain()
        }
    }

    private fun stopOnMain() {
        recognizer?.setRecognitionListener(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun deliver(results: Bundle, onResult: (List<SpeechHypothesis>) -> Unit) {
        val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val hypotheses = texts.mapIndexed { index, text ->
            SpeechHypothesis(text, scores?.getOrNull(index) ?: 0.6f)
        }
        if (hypotheses.isNotEmpty()) onResult(hypotheses)
    }
}

class AndroidSpeechSynthesizer(
    context: Context,
) : SpeechSynthesizer {
    private val ready = AtomicBoolean(false)
    private var pending: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready.set(true)
                applyMediaAttributes()
                val queued = pending
                pending = null
                queued?.invoke()
            }
        },
    )

    private fun applyMediaAttributes() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    override fun speak(
        text: String,
        languageTag: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val utterance = UUID.randomUUID().toString()
        val run = {
            val locale = Locale.forLanguageTag(languageTag)
            val available = tts.setLanguage(locale)
            if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == utterance) onDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == utterance) onError("tts")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == utterance) onError("tts-$errorCode")
                }
            })
            val spoken = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utterance)
            if (spoken == TextToSpeech.ERROR) onError("tts")
        }
        if (ready.get()) run() else pending = run
    }

    override fun stop() {
        pending = null
        runCatching { tts.stop() }
    }

    override fun isLanguageAvailable(languageTag: String): Boolean {
        if (!ready.get()) return true
        val status = tts.isLanguageAvailable(Locale.forLanguageTag(languageTag))
        return status >= TextToSpeech.LANG_AVAILABLE
    }

    fun release() {
        pending = null
        tts.shutdown()
    }
}
