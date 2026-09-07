package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import java.net.URL

internal object DownloadParsing {
    fun normalizeUserInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val extractedUrl = WEB_URL_PATTERN.find(trimmed)?.value
            ?: DOMAIN_URL_PATTERN.find(trimmed)?.value
            ?: trimmed
        val withScheme = if (
            extractedUrl.startsWith("http://", ignoreCase = true) ||
            extractedUrl.startsWith("https://", ignoreCase = true)
        ) {
            extractedUrl
        } else {
            "https://$extractedUrl"
        }

        return withScheme.trimEnd('.', ',', ';', ')', ']', '}', '!', '?')
    }

    fun isDownloadableUrl(url: String): Boolean {
        return runCatching {
            val parsed = URL(url)
            parsed.protocol.equals("http", ignoreCase = true) ||
                parsed.protocol.equals("https", ignoreCase = true)
        }.getOrDefault(false) && runCatching { URL(url).let { it.host.isNotBlank() && it.userInfo == null } }.getOrDefault(false)
    }

    fun detectService(url: String): DownloadService {
        val normalized = url.trim()
        if (normalized.startsWith("ytsearch", ignoreCase = true)) {
            return DownloadService.YOUTUBE
        }

        val parsed = runCatching { URL(normalized) }.getOrNull()
            ?: return DownloadService.UNKNOWN
        if (RemoteDownloadClassifier.isAudioUrl(normalized)) return DownloadService.DIRECT_FILE
        val host = parsed.host.lowercase().removeSuffix(".")
        val path = parsed.path.lowercase()
        return when {
            host.matchesDomain("tiktok.com") -> DownloadService.TIKTOK
            host.matchesDomain("soundcloud.com") || host.matchesDomain("on.soundcloud.com") -> DownloadService.SOUNDCLOUD
            host.matchesDomain("youtube.com") || host.matchesDomain("youtu.be") -> DownloadService.YOUTUBE
            host.matchesDomain("music.yandex.ru") || host.matchesDomain("music.yandex.com") ->
                DownloadService.YANDEX_MUSIC
            host.matchesDomain("vkvideo.ru") || host.matchesDomain("vkvideo.com") ||
                ((host.matchesDomain("vk.com") || host.matchesDomain("vk.ru")) &&
                    (Regex("^/(?:video|clip)").containsMatchIn(path) ||
                        Regex("(?:^|&)(?:z|w)=(?:video|clip)(?:%2d|-|%2f|/|\\d)", RegexOption.IGNORE_CASE)
                            .containsMatchIn(parsed.query.orEmpty()))) -> DownloadService.VK_VIDEO
            host.matchesDomain("vk.com") || host.matchesDomain("vk.ru") || host.matchesDomain("vk.music") -> DownloadService.VK_MUSIC
            host.matchesDomain("music.apple.com") -> DownloadService.APPLE_MUSIC
            host.matchesDomain("spotify.com") || host.matchesDomain("spotify.link") || host == "spoti.fi" -> DownloadService.SPOTIFY
            host.matchesDomain("bandcamp.com") -> DownloadService.BANDCAMP
            host.matchesDomain("instagram.com") || host.matchesDomain("instagr.am") -> DownloadService.INSTAGRAM
            host.matchesDomain("audiomack.com") -> DownloadService.AUDIOMACK
            host.matchesDomain("jamendo.com") -> DownloadService.JAMENDO
            host.matchesDomain("jiosaavn.com") || host.matchesDomain("saavn.com") -> DownloadService.JIOSAAVN
            host.matchesDomain("rutube.ru") -> DownloadService.RUTUBE
            host.matchesDomain("dzen.ru") || host.matchesDomain("frontend.vh.yandex.ru") ||
                ((host.matchesDomain("yandex.ru") || host.matchesDomain("yandex.com")) &&
                    (path.startsWith("/video/") || path.startsWith("/portal/video") || path.startsWith("/portal/efir") ||
                        parsed.query.orEmpty().contains("stream_id=") || host.startsWith("zen."))) -> DownloadService.YANDEX_VIDEO
            host.matchesDomain("deezer.com") || host.matchesDomain("deezer.page.link") -> DownloadService.DEEZER
            host.matchesDomain("boomplay.com") -> DownloadService.BOOMPLAY
            host.matchesDomain("anghami.com") -> DownloadService.ANGHAMI
            host.matchesDomain("pandora.com") -> DownloadService.PANDORA
            host.matchesDomain("zvuk.com") || host.matchesDomain("sber-zvuk.com") -> DownloadService.ZVUK
            host.matchesDomain("music.mts.ru") || host.matchesDomain("music.kion.ru") -> DownloadService.KION_MUSIC
            AMAZON_MUSIC_HOSTS.any { host.matchesDomain(it) } -> DownloadService.AMAZON_MUSIC
            host.matchesDomain("tidal.com") -> DownloadService.TIDAL
            host.matchesDomain("qobuz.com") -> DownloadService.QOBUZ
            host.matchesDomain("beatport.com") -> DownloadService.BEATPORT
            else -> DownloadService.UNKNOWN
        }
    }

    fun filterCookiesForService(service: DownloadService, raw: String): String? {
        val relevantLines = raw.lineSequence()
            .map(String::trimEnd)
            .filter { it.isNotBlank() && !it.startsWith("# This file", ignoreCase = true) }
            .map { line ->
                if (line.startsWith("#HttpOnly_", ignoreCase = true)) {
                    line.removePrefix("#HttpOnly_")
                } else {
                    line
                }
            }
            .filter { line ->
                if (line.startsWith("#")) {
                    return@filter false
                }

                val domain = line.substringBefore('\t').removePrefix(".").lowercase()
                service.cookieDomains.any { cookieDomain ->
                    domain == cookieDomain.lowercase() || domain.endsWith(".${cookieDomain.lowercase()}")
                }
            }
            .toList()

        if (relevantLines.isEmpty()) {
            return null
        }

        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            relevantLines.forEach(::appendLine)
        }.trim()
    }

    fun cookieHeaderFor(url: String, cookiesText: String?): String? {
        val host = runCatching { URL(url).host }.getOrNull() ?: return null
        return cookieHeaderForHost(host, cookiesText)
    }

    fun cookieHeaderForHost(host: String, cookiesText: String?): String? {
        if (cookiesText.isNullOrBlank()) return null
        val normalizedHost = host.lowercase()

        return cookiesText.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 7) return@mapNotNull null
                val domain = parts[0].removePrefix(".").lowercase()
                if (normalizedHost != domain && !normalizedHost.endsWith(".$domain")) return@mapNotNull null
                "${parts[5]}=${parts[6]}"
            }
            .distinct()
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
    }

    fun cookiesFileFromHeaders(domainToHeader: Map<String, String?>): String? {
        val cookieLines = domainToHeader.entries
            .flatMap { (domain, header) -> netscapeCookieLines(domain, header) }
            .distinct()

        if (cookieLines.isEmpty()) return null

        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            cookieLines.forEach(::appendLine)
        }.trim()
    }

    fun netscapeCookieLines(domain: String, cookieHeader: String?): List<String> {
        if (cookieHeader.isNullOrBlank()) return emptyList()

        val normalizedDomain = domain.removePrefix(".").trim().lowercase()
        if (normalizedDomain.isBlank()) return emptyList()

        val cookieDomain = ".$normalizedDomain"
        return cookieHeader.split(';')
            .map(String::trim)
            .mapNotNull { cookie ->
                val separatorIndex = cookie.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex == cookie.lastIndex) return@mapNotNull null

                val name = cookie.substring(0, separatorIndex).trim()
                val value = cookie.substring(separatorIndex + 1).trim()
                if (name.isBlank() || value.isBlank()) return@mapNotNull null

                listOf(
                    cookieDomain,
                    "TRUE",
                    "/",
                    "TRUE",
                    "2147483647",
                    name,
                    value,
                ).joinToString("\t")
            }
            .distinct()
    }

    fun buildYoutubeFallbackQuery(metadata: DownloadSourceMetadata?): String? {
        if (metadata == null) return null

        val queryHint = metadata.queryHint.normalizedOrNull()
        if (queryHint != null) {
            return "$queryHint audio"
        }

        val title = metadata.title.normalizedOrNull() ?: return null
        val artist = metadata.artist.normalizedOrNull()
        return if (artist != null && !title.contains(artist, ignoreCase = true)) {
            "$artist $title audio"
        } else {
            "$title audio"
        }
    }

    fun htmlToSourceMetadata(html: String): DownloadSourceMetadata? {
        val title = extractHtmlMeta(html, "og:title")
            ?: extractHtmlMeta(html, "twitter:title")
            ?: extractHtmlMeta(html, "title")
            ?: extractHtmlTag(html, "title")
        val description = extractHtmlMeta(html, "og:description")
            ?: extractHtmlMeta(html, "description")
            ?: extractHtmlMeta(html, "twitter:description")
        val author = extractHtmlMeta(html, "music:musician_description")
            ?: extractHtmlMeta(html, "author")
            ?: extractHtmlMeta(html, "twitter:creator")

        val normalizedTitle = normalizePageTitle(title?.decodeHtml()).normalizedOrNull()
        val normalizedDescription = description?.decodeHtml()?.normalizedOrNull()
        val normalizedAuthor = author?.decodeHtml()?.normalizedOrNull()

        if (normalizedTitle == null && normalizedDescription == null && normalizedAuthor == null) {
            return null
        }

        val artist = normalizedAuthor
            ?: parseArtist(normalizedDescription)
            ?: extractArtistFromTitle(normalizedTitle)

        val cleanTitle = normalizedTitle?.let { sanitizeTitle(it, artist) }
        val queryHint = listOfNotNull(artist, cleanTitle ?: normalizedTitle)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?: normalizedDescription

        return DownloadSourceMetadata(
            title = cleanTitle ?: normalizedTitle,
            artist = artist,
            queryHint = queryHint,
        )
    }

    fun appleMusicLookupKey(url: String): AppleMusicLookupKey? {
        val match = APPLE_MUSIC_PATTERN.find(url.trim()) ?: return null
        val countryCode = match.groupValues.getOrNull(1).normalizedOrNull()
        val resourceId = match.groupValues.getOrNull(3).normalizedOrNull() ?: return null
        val trackId = match.groupValues.getOrNull(4).normalizedOrNull()
        return AppleMusicLookupKey(
            countryCode = countryCode,
            resourceId = resourceId,
            trackId = trackId,
        )
    }

    private fun extractHtmlMeta(html: String, property: String): String? {
        val escapedProperty = Regex.escape(property)
        val patterns = listOf(
            Regex(
                """<meta[^>]+property=["']$escapedProperty["'][^>]+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']$escapedProperty["']""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta[^>]+name=["']$escapedProperty["'][^>]+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta[^>]+content=["']([^"']+)["'][^>]+name=["']$escapedProperty["']""",
                RegexOption.IGNORE_CASE,
            ),
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private fun extractHtmlTag(html: String, tag: String): String? {
        val escapedTag = Regex.escape(tag)
        return Regex(
            """<$escapedTag[^>]*>(.*?)</$escapedTag>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizePageTitle(raw: String?): String? {
        return raw
            ?.replace(Regex("\\s+"), " ")
            ?.replace(" - YouTube", "", ignoreCase = true)
            ?.replace(" | Spotify", "", ignoreCase = true)
            ?.replace(" - Apple Music", "", ignoreCase = true)
            ?.replace(" - SoundCloud", "", ignoreCase = true)
            ?.replace(" | VK Музыка", "", ignoreCase = true)
            ?.replace(" | Яндекс Музыка", "", ignoreCase = true)
            ?.trim()
    }

    private fun sanitizeTitle(
        title: String,
        artist: String?,
    ): String {
        if (artist == null) return title
        val prefix = "$artist - "
        return if (title.startsWith(prefix, ignoreCase = true)) {
            title.removePrefix(prefix).trim()
        } else {
            title
        }
    }

    private fun extractArtistFromTitle(title: String?): String? {
        val normalized = title.normalizedOrNull() ?: return null
        val separator = TITLE_ARTIST_SEPARATORS.firstOrNull { normalized.contains(it) } ?: return null
        return normalized.substringBefore(separator).normalizedOrNull()
    }

    private fun parseArtist(description: String?): String? {
        val normalized = description.normalizedOrNull() ?: return null
        val separator = DESCRIPTION_SEPARATORS.firstOrNull { normalized.contains(it) }
        return if (separator == null) {
            null
        } else {
            normalized.substringBefore(separator).normalizedOrNull()
        }
    }

    private fun String.decodeHtml(): String {
        return this
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun String.matchesDomain(domain: String): Boolean {
        return this == domain || endsWith(".$domain")
    }

    private val AMAZON_MUSIC_HOSTS = listOf(
        "music.amazon.com", "music.amazon.co.uk", "music.amazon.de", "music.amazon.fr",
        "music.amazon.it", "music.amazon.es", "music.amazon.co.jp", "music.amazon.in",
        "music.amazon.ca", "music.amazon.com.au", "music.amazon.com.br", "music.amazon.com.mx", "amzn.to",
    )

    internal data class AppleMusicLookupKey(
        val countryCode: String?,
        val resourceId: String,
        val trackId: String?,
    ) {
        val lookupId: String
            get() = trackId ?: resourceId
    }

    private val APPLE_MUSIC_PATTERN = Regex(
        """https?://music\.apple\.com/([a-z]{2})/(album|song)/[^/?]+/(\d+)(?:[^\s]*[?&]i=(\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    private val DESCRIPTION_SEPARATORS = listOf(
        " • ",
        " | ",
        " — ",
        " - ",
        "|",
    )

    private val TITLE_ARTIST_SEPARATORS = listOf(
        " - ",
        " — ",
        " | ",
    )

    private val WEB_URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val DOMAIN_URL_PATTERN = Regex(
        """(?:www\.)?(?:[\p{L}\d-]+\.)+[\p{L}]{2,}(?:/[^\s<>"']*)?""",
        RegexOption.IGNORE_CASE,
    )
}
