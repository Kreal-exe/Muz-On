# Muz On

A music player for your own files on Android. Bold poster colours, three looks to switch between, and separate layouts for the outer and inner screens of a foldable.

If you also keep music on a Mac, Muz On can show the playlists of that library: same tracks, same order.

## Features

- Editable home screen: show, hide and reorder sections
- Sort any playlist: as on the Mac, artist, album, title, genre, date added or duration
- The picture embedded in each track, not one per album
- Timed lyrics from the file's tags: tap a line to jump to it
- Swipe the player down, swipe the artwork sideways to change track
- Themes: Poster, Detailed, Aero. Dark, black, light
- 14 languages
- No account, no network, no analytics

## Mac library

The app reads a copy of `Library.musicdb`. To copy it to a phone connected over adb:

```bash
tools/sync-library.sh
```

The file goes to `Music/Music/Music Library.musiclibrary` on the phone; in the app, choose the `Music` folder. Music keeps a playlist's view sort in macOS settings rather than in the library, so the script also writes `playlist-order.txt` from the running Music app.

The database format is undocumented and may change with an update of the Mac app. If it cannot be read, Muz On falls back to the audio files on the phone.

## Build

JDK 25 runs Gradle; `compileSdk` is 37 and `targetSdk` 36.

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Release signing reads `release.keystoreFile`, `release.keystorePassword`, `release.keyAlias` and `release.keyPassword` from `~/.gradle/gradle.properties`.

Translations live in `tools/gen_strings.py`, which writes the string resources.

## Licence

MIT. Fonts: Unbounded and Golos Text under the SIL Open Font License, see `docs/licenses`.
