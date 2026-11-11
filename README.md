# Gambitly

An Android app that teaches chess by practice: opening lines, tactics puzzles, classic games and basic endgames, all played against the computer.

## Build

Requires JDK 17 and the Android SDK (platform 36, NDK 29, CMake 3.22.1).

```
./gradlew assembleDebug
./gradlew test
```

The first native build downloads Stockfish's neural network (40 MB) into
`app/src/main/cpp/nnue/`. Clone with `--recurse-submodules` to get the Stockfish source.

## Release build

Create an upload key once and keep it, with its passwords, somewhere safe (losing it means you cannot update the app):

```
keytool -genkeypair -v -keystore gambitly-upload.jks -alias gambitly -keyalg RSA -keysize 4096 -validity 10000
```

Then add `keystore.properties` in the project root (it is ignored by git):

```
storeFile=gambitly-upload.jks
storePassword=...
keyAlias=gambitly
keyPassword=...
```

```
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab, for Google Play
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk, for direct install
```

To put the release build on a phone connected over USB (with USB debugging on):

```
adb devices                                  # find the phone's serial
adb -s <serial> install -r app/build/outputs/apk/release/app-release.apk

```

A debug build of the same app must be uninstalled first (`adb -s <serial> uninstall io.github.gambitly`),
because it is signed with a different key.

Without `keystore.properties` the release build still works but is left unsigned. Bump `versionCode`
in `app/build.gradle.kts` for every upload, and bump the database version (in `data/Content.kt` and
`tools/build_content.py`) whenever `content.db` changes, or existing installs keep the old content.

## Practice modes

- **Openings**: about 3,900 named lines; the app plays the other side and corrects you when you leave the book.
- **Puzzles**: about 5,300 rated tactics puzzles, filterable by theme, with a personal rating.
- **Classic games**: 22 famous games in "guess the move" form.
- **Endgames**: 15 basic checkmates and key positions against Stockfish at full strength.
- **Free play**: a full game against Stockfish at eight levels.

## Rebuilding the content

`app/src/main/assets/content.db` is generated and checked in. To regenerate it (Python 3.14+):

```
python3 tools/build_content.py                 # everything; streams the Lichess puzzle database
python3 tools/build_content.py --skip-puzzles  # keep the existing puzzles
python3 tools/check_endgames.py /path/to/stockfish   # verify tools/endgames.json
python3 tools/svg_to_vector.py tools/pieces/cburnett app/src/main/res/drawable
```

`./gradlew test` replays every opening, puzzle and game in the database through the rules engine.

## Content and licences

- App code: GPLv3 (see `LICENSE`), because it bundles Stockfish.
- Source: https://github.com/rogerjbos/gambitly
- Openings and puzzles: Lichess open data, CC0.
- Classic games: public-domain move lists.
- Rules library: chesslib (Apache 2.0).

See `CREDITS.md` for details.
