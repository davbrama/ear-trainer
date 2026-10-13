# Ear Trainer

Hands-free interval practice for Android and Android Auto. The app plays two notes, you say the interval, and it answers out loud. Weak intervals come back sooner. English and Hebrew both work for answers, prompts, and commands.

Version 1 is interval naming only. The exercise model, microphone switch, and pitch-detector interface are already in place for later sing-back lessons (play C, sing F, grade the pitch). See [ARCHITECTURE.md](ARCHITECTURE.md).

## Practice loop

1. The app speaks a short intro, then plays two notes: ascending, descending, or together.
2. You name the interval. “Major third”, “major 3rd”, “M3”, “טרצה גדולה”, and “קווינטה זכה” all count.
3. A correct answer is confirmed and the next interval starts. A wrong answer, or “what was that”, speaks the name, replays the same interval, then moves on.
4. You can do the whole session with the screen off. The phone UI is the same session, with buttons for the same actions.

Language follows the device locale until you say “English”, “Hebrew”, or “device language”. If the device has no Hebrew voice, prompts fall back to English and the app says so once.

The microphone permission is the one tap the platform requires. After that, the foreground service keeps listening. “Buttons only” skips the mic if you want the screen controls.

## What it drills

| Set | Intervals |
| --- | --- |
| Beginner | Minor third, major third, perfect fourth, perfect fifth, octave |
| Intermediate | Beginner plus minor and major seconds and sixths |
| Advanced | All twelve, including unison, tritone, and sevenths |

Roots sit around F3–F4. Tempo, direction, and timbre (piano, soft, organ, plucked) are synthesized in the app. There is no sample library.

The scheduler is local and spaced-repetition shaped. A miss comes back in about twenty seconds and lowers ease. Hits stretch the gap, capped at two weeks. New and weak intervals are drawn more often. “Adaptive off” picks uniformly. Nothing is uploaded. The app has no internet permission.

## Voice commands

Commands work in the background, including with the screen off and from Android Auto’s media session, as long as the phone microphone is available. Answers are accepted only while the app is waiting for one. During playback, chatter is ignored and commands such as “skip” and “stop” still land. Global commands beat an interval name, so “stop” is never graded as an answer.

An optional wake phrase is off by default. Turn it on with “wake word on” / “דרוש מילת הפעלה”. The phrases are “ear trainer”, “hey ear trainer”, “מאמן שמיעה”, and “היי מאמן שמיעה”.

“Reset progress” asks you to confirm. Say “yes” or “כן” only then. “No” or “לא” cancels. “Yes” at any other time is not an answer and not a confirmation.

Say “help” or “מה אפשר להגיד” for a short spoken list. The table is the full set. Extra wordings (“lets go”, “hold on”, “one more time”, “whats that”) map to the same actions.

| You want | English | Hebrew |
| --- | --- | --- |
| Start | start, begin, start practice | התחל, בוא נתחיל, התחל תרגול |
| Pause / resume | pause, resume, continue | השהה, המשך |
| Stop | stop, end session | עצור, סיים תרגול |
| Hear it again | repeat, again | שוב, עוד פעם |
| Tempo step | slower, faster | יותר לאט, יותר מהר |
| Exact tempo | tempo 80 (40–200, where 100 is the default speed) | קצב 80 |
| Next / skip | next, skip | הבא, דלג על זה |
| Reveal | what was that, tell me the answer | מה זה היה, מה התשובה |
| Easier / harder | easier, harder | יותר קל, יותר קשה |
| Interval set | beginner, intermediate, advanced | מתחילים, בינוני, מתקדם |
| Direction | ascending, descending, harmonic, mixed | עולה, יורד, הרמוני, מעורב |
| Timbre | piano, soft, organ, plucked | פסנתר, רך, אורגן, פריטה |
| Language | english, hebrew, device language | אנגלית, עברית, שפת המכשיר |
| Session length | 15 minutes, session 20 minutes (1–120) | תרגול 15 דקות |
| Adaptive drill | adaptive on, adaptive off | למידה מסתגלת, בלי למידה מסתגלת |
| Wake phrase | wake word on, wake word off | דרוש מילת הפעלה, בלי מילת הפעלה |
| Background mic | start listening, stop listening | התחל להקשיב, תפסיק להקשיב |
| Stats | how am I doing, stats | מה המצב, איך אני |
| Help | help, what can I say | עזרה, מה אפשר להגיד |
| Reset (then confirm) | reset progress | אפס התקדמות |
| Confirm / cancel | yes, no | כן, לא |
| Feedback | send feedback, then dictate, then “send” | שלח משוב, then “שלח” |
| Feedback in one breath | feedback the piano is too bright | משוב הפסנתר חד מדי |
| Export notes | export feedback | ייצא משוב |

Every control on the phone is one of these actions. Feedback is saved on the device. Export opens a share sheet. There is no remote channel until a `FeedbackChannel` is configured.

Interval names the recognizer should hear include “perfect fifth”, “minor 3rd”, “aug 4th”, “tritone”, “P5”, “M3”, “m3”, “TT”, “קוורטה זכה”, “קוורטה טהורה”, “קווינטה זכה”, “אוקטבה”, “סקונדה קטנה”, “סקונדה גדולה”, “טריטון”, “קוורטה מוגדלת”, and “קווינטה מוקטנת”. Bare “third” or “טרצה” is ambiguous and is not accepted. “רביעית” is a perfect fourth. “חמישית” is a perfect fifth.

If a phrase matches neither an answer nor a command, a `Coach` may interpret it later. The shipped coaches return nothing, so the app asks you to repeat. No API key is required.

## Android Auto

The app is a **media** app. It exposes a Media3 `MediaLibraryService` and media session. It does not use the Car App Library, and it does not pretend to be navigation, a POI, or a messaging app.

That is a platform constraint. Android for Cars allows in-app audio while driving for media apps (and for navigation voice guidance). Other categories cannot run this kind of drill. The car UI is the standard media template: browse, play, pause, and up to four custom buttons. Those buttons are Repeat, Skip, Reveal, and Easier. Harder and the rest of the settings are voice or the phone. The media title never contains the answer.

Browse groups are Practice (beginner, intermediate, advanced), Direction, and Start practice. A voice search such as “play intervals on Ear Trainer” starts a session through the media browser search path.

### Limits

- Recognition uses the **phone or headset microphone**, not the car Assistant microphone. When Assistant takes audio focus, the drill pauses.
- The Desktop Head Unit does not type a free-form “perfect fifth” into the app. Use the phone mic, or the custom buttons, while the DHU shows the media session.
- `SpeechRecognizer` may use the device’s network recognizer if offline language packs are missing. Parsing, scheduling, and synthesis stay on the device. The app never uploads audio itself.
- Hebrew recognition and Hebrew TTS depend on language data installed on the phone.
- The service starts when you open the app or pick it in the car. It does not start at boot.
- A partial wake lock stays held while the service runs, up to two hours.
- Android Automotive OS can bind the same media service. There is no separate AAOS APK in this version.

### Desktop Head Unit

1. On the phone: Android Auto → Developer settings → enable developer mode and the head unit server.
2. Install the head unit: `sdkmanager "extras;google;auto"`.
3. Plug in the phone, then `adb forward tcp:5277 tcp:5277` and start `desktop-head-unit`.
4. Open Ear Trainer in the media list. Play starts a drill. The custom buttons and the phone mic cover answers.

## Build and sideload

Requirements: JDK 17, Android SDK 35, build-tools 35.0.0. `local.properties` may set `sdk.dir`. Otherwise `ANDROID_HOME` is enough.

```bash
./gradlew :core:test :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant the microphone when the app asks. Notification permission lets the ongoing practice notification stay visible.

If the process crashes, the stack trace is written to app storage. The next launch shows it at the top of the screen, with share and clear. A debug build can force one report with `adb shell am start -n dev.eartrainer/.MainActivity -a dev.eartrainer.DEBUG_CRASH`.

```bash
./gradlew :core:test
```

Tests cover interval layout, sing-back grading, the scheduler, English and Hebrew answers (including “major 3rd” and the Hebrew names above), the command catalog, and the rule that speech output never shares the mic with recognition or pitch capture.

## Layout

- `:core` — exercise model, scheduler, parsers, session, synth, audio-turn policy. No Android imports.
- `:app` — Compose UI, Media3 service, `SpeechRecognizer` / `TextToSpeech`, `AudioRecord`, ExoPlayer playback, JSON files under the app files directory.

Speech and pitch stay behind interfaces. Version 1 does not include a pitch tracker. `StubPitchDetector` is the stand-in. Details are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Roadmap

- Sing-back lessons: a YIN or pYIN `PitchDetector` on the PCM stream, then deal `INTERVAL_SINGBACK` from the session.
- Chord naming, melody dictation, and rhythm, using the same exercise type and expected-answer shape.
- An on-device speech model behind `SpeechTranscriber`, if the platform recognizer is not enough.
- An LLM `Coach` for ambiguous requests, spoken critique, and a session summary. It must return the existing `VoiceAction` types.
- A real `FeedbackChannel` if notes should leave the phone.

## License

[MIT](LICENSE).
