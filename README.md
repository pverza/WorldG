# Wortschatz

A small, ad-free Android app for learning German vocabulary from English.

- 814 words from A1 to C1, with English translations and example sentences
- Pick your levels on first launch (and change them any time in Settings)
- Quiet word cards in your notifications and on the lock screen, with **I know it** / **Still learning** buttons
- Optional word card that slides in right after you unlock your phone
- Spaced repetition (Leitner boxes): words you struggle with come back sooner
- In-app flashcards and progress per level
- No ads, no tracking, no internet permission, works fully offline

## Battery and resources

- Notification cards use one inexact, non-waking repeating alarm, no network, no wake locks
- Cards are silent (low-priority channel) and each new card replaces the previous one
- Configurable interval (1 to 6 hours) and active hours; nothing is shown at night
- The unlock card is purely event-driven (it does nothing between unlocks), limited to at most one
  card per 5 to 60 minutes, and optional. Android requires a small silent ongoing notification
  for the service that listens for unlocks, and the "Display over other apps" permission
- Minified release build; the whole vocabulary is a few dozen KB held in memory

## Getting the APK

Every push to `main` builds the app with GitHub Actions and attaches `Wortschatz.apk` to the
**latest** release (Releases page of this repo). Open the file on your phone to install it.
Builds are signed with a fixed key (`app/wortschatz.keystore`), so a new APK installs over the old
one and keeps your progress. Builds from before version 1.1 used a different key: uninstall that
old version once before installing 1.1.

## Building locally

Open the folder in Android Studio, or run `gradle assembleRelease` with Gradle 8.9+ and JDK 17.
The APK is written to `app/build/outputs/apk/release/`.

## Adding words

Edit `app/src/main/assets/words.json`. Each entry has a unique `id` (never reuse or renumber ids,
progress is stored per id), `de`, `en`, an example `ex` with its translation `exEn`, and a level
`lvl` (A1, A2, B1, B2 or C1). A new level also needs adding to `LEVELS` in `Words.kt`.
