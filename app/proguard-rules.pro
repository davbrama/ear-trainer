# Debug builds are not minified. Keep the stub pitch detector and coach types
# available for a future release build.
-keep class dev.eartrainer.core.audio.StubPitchDetector { *; }
-keep class dev.eartrainer.core.coach.** { *; }
