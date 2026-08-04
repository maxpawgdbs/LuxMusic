# LuxMusic

[![Release APK](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml)

Android music player with an offline-first local library, playlists, link downloads, and Media3 playback.

## Current features

- Offline local library stored inside the app sandbox.
- Playlist management.
- Shuffle, repeat-all, and repeat-one playback modes.
- Fast audio-only link downloads through `yt-dlp` for YouTube, TikTok, SoundCloud, and other supported sites.
- Metadata-based matching for Spotify, Apple Music, Яндекс Музыка, and VK Музыка links.
- Foreground media playback notification with progress bar and transport controls.
- Jetpack Compose UI on Material 3.

## Stack

- Kotlin
- Jetpack Compose
- Media3 ExoPlayer
- `io.github.junkfood02.youtubedl-android`

## CI/CD

- Commits pushed to `main` update the prerelease `edge` build.
- Version tags publish a stable GitHub Release.
- Every workflow run tests the app, runs release lint, and builds one signed universal APK.

## Stable APK updates

- GitHub Releases publish a signed `LuxMusic-<version>-universal.apk` built with the bundled keystore at `signing/luxmusic-dev.jks`.
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
