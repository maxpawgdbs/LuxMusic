# Download module

LuxMusic runs directly on Android without a server or Docker. The pipeline separates URL parsing, metadata lookup, execution, native extraction and durable library import.

## YouTube and TikTok

The reference implementation is [nekotyy/tiktok-bot](https://github.com/nekotyy/tiktok-bot/blob/14e286cfceaaecaeba5142b796062e97ceffa69e/bot/services/downloader.py). Its relevant ideas are yt-dlp with a JavaScript runtime, explicit stream selection and FFmpeg processing, bounded retries, and a public TikWM fallback for TikTok. LuxMusic adapts these to audio on Android; gallery-dl and Telegram video delivery are not needed.

- `YtDlpMediaDownloadBackend` uses `io.github.deniscerri.youtubedl-android:library:0.19.0`, which packages QuickJS (`libqjs.so`) and passes its path to yt-dlp. FFmpeg is initialized before audio extraction.
- The compatible yt-dlp 2026.08.19 executable, including EJS, is bundled in the APK and checked by SHA-256. See [bundle provenance and update instructions](bundled-extractor.md). First use does not depend on a successful runtime update; newer installed nightly extractors are preserved.
- Audio streams are preferred; a combined video/audio stream is accepted and its audio is extracted. Video-only streams are excluded. Partial files are not imported; validation inspects the actual audio stream instead of trusting sidecar duration.
- Known extractor/signature failures can refresh nightly yt-dlp once and retry. Network timeouts, HTTP access/rate limits, login requirements, missing formats, cancellation and library-write failures do not trigger updates. Even a failed update is attempted only once per downloader instance. The updater has a 15-second socket timeout and a 45-second overall deadline.
- `TikTokFallbackBackend` queries the same public TikWM endpoint as the reference after native extraction fails, before waiting for an extractor update. Native TikTok remains first because its audio-only stream was the faster path in the live check. The fallback extracts clip audio, or a photo post's music, preserves title/artist, and stores the original source link. No credentials are sent. TikWM is a third-party availability dependency.
- Version 0.7.1 removes the one-second YouTube inter-request sleep, uses eight concurrent fragments and a 256 KiB native buffer, bounds request retries, and uses a 12-second socket timeout. Combined streams prefer video at 480p or below when no audio-only stream exists. Audio uses `--audio-format best`; no forced MP3 transcode. Live broadcasts are excluded.
- Native requests inherit the HTTP proxy selected by Android's `ProxySelector`, including a direct/bypassed-host selection. Python otherwise ignores Android's Java/Wi-Fi proxy configuration. No proxy is hardcoded in the APK; no TLS verification is disabled. PAC fallback rotation and authenticated proxies are not implemented.
- YouTube downloads are anonymous. The cookie UI and account store were removed; old account preferences are unused. Restricted content yields an on-screen error.

## Other sources

Yandex Music keeps the native authenticated pipeline for tracks, albums and artists. Tracks are imported in small batches so discographies do not retain all artwork in memory. `DownloadPlatformPolicy` is the shared registry for routing and UI disclosure; see the [complete platform matrix](platforms-0.7.1.md). Native sources skip redundant metadata requests. Catalog matching is explicitly labelled as a YouTube result, not a download of the platform's original file. Missing metadata produces an error rather than an arbitrary search. Apple Music, Tidal, Qobuz and preview-only Beatport are deferred.

`RemoteFileTransfer` identifies and streams direct audio in one GET, including extensionless URLs. A 128 KiB buffer, 1 GiB limit, bounded redirects, byte-count checks and actual audio validation protect the import. HTML login/error pages are not tracks. Cancellation removes temporary partial files. HTTPS-to-HTTP redirects and embedded URL credentials are rejected. User-entered HTTP audio is supported, but is unencrypted; prefer HTTPS. ZIP archives retain bounded extraction and existing import paths.

`DownloadedAudioPolicy` rejects explicit previews and unexpectedly short native downloads using the sidecar's full duration and actual audio duration. The sidecar alone never proves that an audio stream exists. Platform access restrictions and DRM are not bypassed.

## Storage and errors

The package, signing key, track IDs, playlist IDs, JSON fields and `files/luxmusic` paths remain in place. Atomic writes update the primary manifest and a separate backup. A recovered corrupt original is retained. Partly readable or unrecoverable manifests are protected against writes, avoiding silent replacement with an empty collection.

`AppMessages` queues operation failures for the screen's snackbar. Coroutine cancellation propagates. Playback errors and failures to launch Android activities/services are handled at their operation boundaries; the application does not try to resume an arbitrarily damaged process through a global uncaught-exception handler.

## Verification without Docker

Use JDK 21 and Android SDK 36:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
./gradlew -Pluxmusic.emulator=true connectedDebugAndroidTest
```

`offlineUnitTest` remains an alias for `testDebugUnitTest`. Dependencies must be cached before using Gradle `--offline`. Emulator mode adds x86_64 for local tests; release APKs normally include ARM64 and ARMv7 only. On Windows use an ASCII checkout and Gradle cache path if Java cannot load `GradleWorkerMain` from a path containing non-ASCII characters.

### Updating an actual release APK

Use an **empty disposable test installation only**, capable of running the release APK's ARM libraries. Never uninstall a real user's application to run this check. `UpgradeFixtureInstrumentation` refuses to seed an existing catalog or audio directory.

Build its test APK with `./gradlew -Pluxmusic.emulator=true -Pluxmusic.upgradeTest=true assembleDebugAndroidTest`. This selects a Java/platform-API runner, because the normal AndroidX test runner depends on Kotlin classes that R8 renames in production APKs.

1. Install the previous official release and the generated `app-debug-androidTest.apk`.
2. Run `adb shell am instrument -w -e upgradePhase seed com.luxmusic.android.test/com.luxmusic.android.data.UpgradeFixtureInstrumentation`.
3. Install the new signed release with `adb install -r new-release.apk`, without uninstalling or clearing data.
4. Run the same instrumentation command with `-e upgradePhase verify`. It checks exact audio bytes, the original path and ID, track metadata and playlist membership.
5. Open the application and check that the track and playlist appear. Rebuild without `luxmusic.upgradeTest` for the regular AndroidX regression suite.

Repeat the cold-start check with `-e upgradePhase preparePausedQueue` and then `prepareMissingQueue` on the disposable fixture installation. After each preparation, launch the app and leave it open for at least 15 seconds, then verify that the library is visible and the process is still alive. This covers Android's delayed foreground-service timeout: UI commands use `startService`, while Media3 promotes actual playback itself. Restoring a paused or missing queue must not promise immediate foreground playback.

Stable tag builds upload their signed APK to a draft GitHub release. Download that exact asset, verify its signature and upgrade/cold-start behavior, and only then publish the draft. Branch builds continue to update the automatic Edge prerelease.
