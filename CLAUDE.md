# Working on this repo

Personal pet project. Standalone; no ties to other projects.

- Commit and push directly to `main`. No feature branches, no pull requests.
- Don't ask about trivial decisions. Pick what you think is best, do it, and mention it briefly.
- Move fast. Run `./gradlew :core:test :app:assembleDebug` before pushing; that's the bar.
- Releases: push a `vX.Y.Z` tag; CI builds the debug APK and publishes a GitHub Release. Bump `versionName`/`versionCode` in `app/build.gradle.kts` first.
