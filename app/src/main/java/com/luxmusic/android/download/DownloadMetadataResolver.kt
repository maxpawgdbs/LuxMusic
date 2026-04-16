package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class CompositeDownloadMetadataResolver(
    private val backend: MediaDownloadBackend,
    private val httpClient: MetadataHttpClient,
) {
    fun resolve(
        url: String,
        service: DownloadService,
        session: DownloadSession?,
    ): DownloadSourceMetadata? {
        return when (service) {
            DownloadService.APPLE_MUSIC -> {
                resolveAppleMusic(url)
                    ?: resolveHtml(url, service, session)
            }

            DownloadService.SPOTIFY -> {
                resolveOEmbed(url, service)
                    ?: resolveHtml(url, service, session)
            }

            DownloadService.YOUTUBE,
            DownloadService.TIKTOK,
            DownloadService.SOUNDCLOUD,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
            DownloadService.UNKNOWN,
            -> {
                backend.fetchInfo(url, service, session)
                    ?: resolveOEmbed(url, service)
                    ?: resolveHtml(url, service, session)
            }
        }
    }

    private fun resolveAppleMusic(url: String): DownloadSourceMetadata? {
        val lookupKey = DownloadParsing.appleMusicLookupKey(url) ?: return null
        val endpoint = buildString {
            append("https://itunes.apple.com/lookup?id=")
            append(lookupKey.lookupId)
            append("&entity=song")
            lookupKey.countryCode?.takeIf { it.isNotBlank() }?.let { countryCode ->
                append("&country=")
                append(countryCode)
            }
        }

        val payload = httpClient.getText(
            endpoint,
            headers = mapOf("Accept" to "application/json"),
        ) ?: return null

        val songBlock = SONG_BLOCK_REGEX.find(payload)?.value ?: payload
        val title = extractJsonString(songBlock, "trackName")
        val artist = extractJsonString(songBlock, "artistName")
        val album = extractJsonString(songBlock, "collectionName")
        val durationMs = extractJsonLong(songBlock, "trackTimeMillis")
        val queryHint = listOfNotNull(artist, title)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        return DownloadSourceMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            queryHint = queryHint,
        ).takeIf { it.title != null || it.artist != null }
    }

    private fun resolveOEmbed(
        url: String,
        service: DownloadService,
    ): DownloadSourceMetadata? {
        val endpoint = oEmbedEndpoint(url, service) ?: return null
        val payload = httpClient.getText(
            endpoint,
            headers = mapOf("Accept" to "application/json"),
        ) ?: return null

        val rawTitle = extractJsonString(payload, "title")
        val rawAuthor = extractJsonString(payload, "author_name")
        val normalized = normalizeOEmbedTitle(rawTitle, rawAuthor)

        return DownloadSourceMetadata(
            title = normalized.first,
            artist = normalized.second,
            queryHint = listOfNotNull(normalized.second, normalized.first)
                .joinToString(" ")
                .takeIf { it.isNotBlank() },
        ).takeIf { it.title != null || it.artist != null }
    }

    private fun resolveHtml(
        url: String,
        service: DownloadService,
        session: DownloadSession?,
    ): DownloadSourceMetadata? {
        val headers = buildMap {
            put("User-Agent", session?.userAgent ?: FALLBACK_USER_AGENT)
            DownloadParsing.cookieHeaderFor(url, session?.cookiesText)?.let { cookieHeader ->
                put("Cookie", cookieHeader)
            }
        }

        return httpClient.getText(url, headers)
            ?.let(DownloadParsing::htmlToSourceMetadata)
    }

    private fun oEmbedEndpoint(url: String, service: DownloadService): String? {
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
        return when (service) {
            DownloadService.YOUTUBE -> "https://www.youtube.com/oembed?format=json&url=$encodedUrl"
            DownloadService.TIKTOK -> "https://www.tiktok.com/oembed?url=$encodedUrl"
            DownloadService.SOUNDCLOUD -> "https://soundcloud.com/oembed?format=json&url=$encodedUrl"
            DownloadService.SPOTIFY -> "https://open.spotify.com/oembed?url=$encodedUrl"
            else -> null
        }
    }

    private fun normalizeOEmbedTitle(
        rawTitle: String?,
        rawAuthor: String?,
    ): Pair<String?, String?> {
        val title = rawTitle?.trim()
        val author = rawAuthor?.trim()

        if (title == null) {
            return null to author
        }

        if (author != null && title.startsWith("$author - ", ignoreCase = true)) {
            return title.removePrefix("$author - ").trim().normalizedOrNull() to author.normalizedOrNull()
        }

        return title.normalizedOrNull() to author.normalizedOrNull()
    }

    private fun extractJsonString(
        payload: String,
        field: String,
    ): String? {
        return Regex(
            "\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")
            ?.replace("\\\"", "\"")
            ?.replace("\\n", " ")
            ?.normalizedOrNull()
    }

    private fun extractJsonLong(
        payload: String,
        field: String,
    ): Long? {
        return Regex(
            "\"${Regex.escape(field)}\"\\s*:\\s*(\\d+)",
            RegexOption.IGNORE_CASE,
        ).find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private companion object {
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; LuxMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

        val SONG_BLOCK_REGEX = Regex(
            """\{[^{}]*"kind"\s*:\s*"song"[^{}]*}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
