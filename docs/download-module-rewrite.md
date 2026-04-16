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
- `Yandex Music`: direct extractor first, then YouTube metadata fallback
- `VK Music`: direct extractor first, then YouTube metadata fallback
- `Apple Music`: metadata extraction from Apple lookup endpoint, then YouTube metadata fallback
- `Spotify`: metadata extraction from Spotify oEmbed endpoint, then YouTube metadata fallback

## Libraries and APIs used

- `io.github.junkfood02.youtubedl-android`
  Android wrapper around `yt-dlp`, used for direct extractor downloads and extractor info probing.
- Apple lookup endpoint
  Used to resolve track metadata from Apple Music links without depending on a user session.
- Spotify oEmbed endpoint
  Used to resolve title and artist from Spotify links without direct audio access.
- HTML/OpenGraph metadata fallback
  Used when a service has no reliable public metadata endpoint for the exact URL.

## Notes

- Apple Music and Spotify are not treated as direct-file sources because the new module is built around public metadata plus fallback matching, not DRM stream extraction.
- Yandex Music and VK Music keep session support because extractor success for those services is often tied to a valid user session.
- The test suite covers workflow scenarios for every requested platform and verifies the exact routing path used by the new module.
