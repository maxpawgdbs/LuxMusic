# Bundled yt-dlp

`app/src/main/assets/yt-dlp` is the official platform-independent Python zip executable, intentionally versioned as an APK dependency (not a generated build artifact).

- Version: **2026.08.19**
- Source: https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19
- Asset: https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp
- Size: 3,072,469 bytes
- SHA-256: `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`

The checksum matches the GitHub release asset digest and is verified by Gradle before each build. The archive includes `yt_dlp_ejs` and its JavaScript solver. The Android wrapper provides Python, FFmpeg and QuickJS. Its own older yt-dlp bundle lacks `--js-runtimes`, so using the wrapper alone fails even for local audio.

`BundledExtractorInstaller` atomically installs this extractor in the wrapper's private **no-backup runtime directory**, before initializing Python. It reads the installed version and preserves same-version or newer nightly updates. This directory is separate from `files/luxmusic`, which contains users' music and playlists.

When updating the dependency, fetch an official release, verify its published SHA-256, update this file, the Gradle checksum and `BundledExtractorInstaller.VERSION`, and run the native Android download tests. Those tests use a local HTTP audio fixture and require no internet access.
