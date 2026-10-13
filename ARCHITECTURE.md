# Architecture

Ear Trainer is two modules. `:core` is plain Kotlin: intervals, the exercise model, the adaptive scheduler, answer and command parsing, the session state machine, and the audio-turn policy. `:app` is Android: Jetpack Compose, a Media3 media-library service, tone playback, and the platform speech and microphone adapters. The session never calls `SpeechRecognizer`, `TextToSpeech`, or `AudioRecord` directly.

## Exercise model

Every drill is an `Exercise`:

| Field | Role |
| --- | --- |
| `type` | `INTERVAL_NAMING` today. `INTERVAL_SINGBACK`, `CHORD_NAMING`, `MELODY_DICTATION`, and `RHYTHM_DICTATION` are reserved. |
| `notes` | What to play: MIDI, start time, duration. |
| `expected` | `SpokenName(interval)` or `SungPitch(midi, toleranceCents)`. |
| `modality` | `SPOKEN_NAME` or `SUNG_PITCH`. This chooses the microphone path. |

Version 1 only deals naming exercises. `IntervalExercises.singback` already builds a sing-back item (play the root, expect the upper note within 50 cents). `AnswerGrader` routes spoken names to the lexicon and sung pitches to `SingbackGrader`. An octave error fails. A null or low-confidence pitch fails. The session ignores pitch events unless the current exercise expects a sung pitch.

## Speech and pitch input

`SpeechTranscriber` and `SpeechSynthesizer` are the speech ports. Version 1 implements them with Android `SpeechRecognizer` and `TextToSpeech`. There is no Whisper model and no network permission. A later on-device recognizer implements the same interface.

`PcmCapture` is the raw-mic port (`AudioRecord`, 16-bit mono). `PitchDetector.detect(samples, sampleRate)` returns a frequency and a confidence, or null. `StubPitchDetector` always returns null. A YIN or pYIN tracker can replace that class without touching the session.

`SwitchingAudioInput` owns the microphone. It stops speech recognition before opening PCM, and stops PCM before opening speech recognition. The two consumers never run together.

## Who owns the speaker and the mic

`AudioTurnPolicy` returns an output intent, a mic mode, and a focus request for the current phase:

- **Playing tones.** Hold media focus. Speech mic stays open so "skip" and "stop" can barge in. The router drops anything that is not a command. Pitch capture stays closed so the tracker cannot grade the app's own tone.
- **Speaking.** Hold media focus. Mic closed, so the recognizer does not hear the prompt.
- **Waiting for an answer.** Hold media focus (releasing it would hand the car radio back). Spoken-name exercises use speech recognition. Sung-pitch exercises use PCM plus `PitchDetector`.
- **Confirm / dictate feedback.** Speech mic, media focus held.
- **Idle or paused.** Mic only if "keep listening" is on. Focus released.

`AndroidAudioFocus` requests `AUDIOFOCUS_GAIN` for a held drill, keeps the same request across phases, and abandons it on pause or idle. ExoPlayer is created with `handleAudioFocus = false` so this port, not the player, decides.

## Who may pause

`PauseArbiter` (in `:core`) is the only gate into `VoiceAction.Pause`. Every request carries its source: voice command, app button, media controller (notification, headset, Android Auto), or audio-focus loss.

- Voice "pause" / "hold on" / "השהה" / "רגע" and the on-screen button always pause.
- Focus losses are attributed by time to our own clients. Each client has its own window: TTS from the start of a prompt until 1.5 s after it ends, and the recognizer for 3 s after each `startListening` (the system recognizer takes transient focus to mute media). A loss inside either window is ours and is ignored.
- Focus losses and media-controller pauses are ignored for 2.5 s after start or resume.
- Recognizer errors and end-of-speech never pause.

`PauseLog` records each decision with its source and reason, plus focus changes and recognizer errors. "Share report" (crash banner and Settings) sends it with the crash log.

## Other extension points

`Coach` is the pluggable interpreter and spoken critic. `Coaching` asks an unconfigured LLM coach first, then a local coach that returns null. Both are stubs. A later model must return a `VoiceAction`, the same type the rule parser and the phone UI already emit.

`FeedbackChannel` is the optional outbound path for dictated notes. Version 1 stores them in app files and `LocalOnlyFeedbackChannel` reports that no remote channel is configured. Export shares the local text file.
