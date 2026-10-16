# Wortschatz

A small, ad-free Android app for learning German vocabulary from English.

- 171 words (A1, A2, B1) with English translations and example sentences
- Quiet word cards in your notifications and on the lock screen, with **I know it** / **Still learning** buttons
- Spaced repetition (Leitner boxes): words you struggle with come back sooner
- In-app flashcards and progress per level
- No ads, no tracking, no internet permission, works fully offline

## Battery and resources

- One inexact, non-waking repeating alarm; no foreground service, no wake locks, no network
- Cards are silent (low-priority channel) and each new card replaces the previous one
- Configurable interval (1 to 6 hours) and active hours; nothing is shown at night
- Minified release build; the whole vocabulary is a few KB held in memory

## Getting the APK

Every push to `main` builds the app with GitHub Actions and attaches `Wortschatz.apk` to the
**latest** release (Releases page of this repo). Open the file on your phone to install it.

## Building locally

Open the folder in Android Studio, or run `gradle assembleRelease` with Gradle 8.9+ and JDK 17.
The APK is written to `app/build/outputs/apk/release/`.

## Adding words

Edit `app/src/main/assets/words.json`. Each entry has a unique `id` (never reuse or renumber ids,
progress is stored per id), `de`, `en`, an example `ex` with its translation `exEn`, and a level
`lvl` (A1, A2 or B1; new levels also need adding to `SettingsScreen` and `ProgressScreen`).
