# Download Module Rewrite

## Why the module was rewritten

The old `LinkDownloader` mixed together:

- service detection
- cookies and session handling
- metadata extraction
- direct extractor calls
- fallback search logic
- file validation and import

The new implementation splits those responsibilities into:

- `DownloadPlanner`
- `CompositeDownloadMetadataResolver`
- `LinkDownloadExecutor`
- `YtDlpMediaDownloadBackend`
- thin Android adapter `LinkDownloader`

This makes the workflow testable on the JVM without Android runtime dependencies.

## Platform strategy

- `YouTube`: direct extractor download via `yt-dlp`
- `TikTok`: direct extractor download via `yt-dlp`
- `SoundCloud`: direct extractor download via `yt-dlp`

## Libraries and APIs used

- `io.github.junkfood02.youtubedl-android`
  Android wrapper around `yt-dlp`, used for direct extractor downloads and extractor info probing.
- HTML/OpenGraph metadata fallback
  Used as a last-resort metadata hint when extractor probing does not return enough information.

## Notes

- The supported download surface is intentionally narrow now: only `YouTube`, `TikTok`, and `SoundCloud`.
- A YouTube session can still be attached to reduce `429` and similar rate-limit failures.
- The test suite now verifies only the supported direct-download platforms plus explicit rejection of unsupported links.
