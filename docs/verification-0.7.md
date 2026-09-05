# Verification for 0.7

Local verification on 2026-09-05 used JDK 21, SDK 36 and a dedicated Android 15/API 35 emulator. No Docker was used.

- **102 JVM tests:** passed, zero failures or errors.
- **13 Android regression tests:** passed. The regular runner reports 14 tests because the optional live-network test is skipped unless a URL is supplied.
- Android coverage includes rapid navigation, removal of the YouTube cookie UI, visible operation errors, library import, legacy/corrupt index recovery, the full playback queue and native QuickJS execution.
- Native download checks serve local WAV and generated combined MP4 fixtures over HTTP, extract playable audio through yt-dlp/FFmpeg, and check duration with Android's media APIs. The MP4 check covers direct media whose codec is unknown before download.
- **Release build, lint and R8 keep-rule verification:** passed. The universal APK contains ARM64 and ARMv7 libraries, keeps `com.luxmusic.android`, and has version name `0.7` / local version code `7000000`.
- **Real APK upgrade:** the official GitHub `v0.6.2` APK was installed, seeded with a test WAV and playlist, then updated with the minified 0.7 release using `adb install -r`. Audio bytes, path, ID, metadata and playlist membership were unchanged. The track was visible in both releases.
- The old and new signing certificate SHA-256 is `2722807a9cc2b6c14042f32534589ce25290abfbc0b822a59a9a3b05cc8288e6`.

## Limits of the checks

Live internet checks are separate from the deterministic regression suite. The YouTube test URL `BaW_jenozKc` reached the 180-second test deadline. The TikTok test URL ending in `6718335390845095173` reached the media download stage after the format-filter fix, but its network reads timed out after retries. Successful end-to-end downloads from those sites therefore remain unconfirmed in this local network; these results must not be reported as passed live tests. Authenticated Yandex downloads were not exercised with a real account.

See [the download module documentation](download-module-rewrite.md#updating-an-actual-release-apk) for commands to repeat the APK upgrade check on an empty disposable test installation. Install updates over existing applications; never uninstall a user's application or clear its data to upgrade.
