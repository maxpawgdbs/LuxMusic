# LuxMusic

[![Android CI](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/ci.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/ci.yml)
[![Release APK](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml)
[![Nightly APK](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/nightly.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/nightly.yml)

Android music player prototype with an offline-first local library, playlists, link downloads, and Media3 playback.

## Current features

- Offline local library stored inside the app sandbox.
- Playlist management.
- Shuffle, repeat-all, and repeat-one playback modes.
- Link downloads through `yt-dlp` for YouTube, TikTok, and SoundCloud only.
- Foreground media playback notification with progress bar and transport controls.
- Jetpack Compose UI on Material 3.

## Stack

- Kotlin
- Jetpack Compose
- Media3 ExoPlayer
- `io.github.junkfood02.youtubedl-android`

## CI/CD

- `Android CI` runs on every push to `main`, on every pull request, and manually.
- `Release APK` runs on every push to `main`, on tags matching `v*`, and manually.
- `Nightly APK` runs on schedule and manually.
- GitHub Actions no longer uploads build artifacts into Actions storage.
- GitHub Releases publish one signed `arm64-v8a` release APK for real devices.

## Stable APK updates

- GitHub Releases always publish a signed `app-arm64-v8a-release.apk` built with the bundled keystore at `signing/luxmusic-dev.jks`.
- Release workflow auto-increments `versionCode`, so every new `edge` build can be installed over the previous one without deleting the app and its local database.
- The base app version is tracked through `luxmusic.baseVersion` in `gradle.properties`.
- If you replace `signing/luxmusic-dev.jks` with another certificate, Android will require one reinstall. After that, updates will continue only between builds signed with the new certificate.

## Important notes

- The current downloader stack depends on a GPL component. Replace it before shipping a proprietary distribution.
- Download only content you have the right to store offline.

## Local setup

1. Install Android Studio with JDK 17+.
2. Install Android SDK Platform 36.
3. Open the project in Android Studio.
4. Let Gradle sync finish.
5. Run the `app` target on a device or emulator with Android 8.0+.
