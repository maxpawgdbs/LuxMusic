package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService

internal data class DownloadSourceMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val queryHint: String? = null,
)

internal object DownloadParsing {
    fun detectService(url: String): DownloadService {
        val normalized = url.lowercase()
        return when {
            "music.yandex" in normalized || "yandex.ru" in normalized -> DownloadService.YANDEX_MUSIC
            "vk.com" in normalized || "vkvideo.ru" in normalized || "vk.ru" in normalized -> DownloadService.VK_MUSIC
            "tiktok.com" in normalized || "vm.tiktok.com" in normalized -> DownloadService.TIKTOK
            "music.apple.com" in normalized || "itunes.apple.com" in normalized -> DownloadService.APPLE_MUSIC
            "spotify.com" in normalized -> DownloadService.SPOTIFY
            "soundcloud.com" in normalized -> DownloadService.SOUNDCLOUD
            "youtube.com" in normalized || "youtu.be" in normalized || "music.youtube.com" in normalized ||
                normalized.startsWith("ytsearch") -> DownloadService.YOUTUBE
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

    fun cookieHeaderFor(host: String, cookiesText: String?): String? {
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
            ?: extractHtmlTag(html, "title")
        val description = extractHtmlMeta(html, "og:description")
            ?: extractHtmlMeta(html, "description")
            ?: extractHtmlMeta(html, "twitter:description")

        val normalizedTitle = title?.decodeHtml()?.normalizedOrNull()
        val normalizedDescription = description?.decodeHtml()?.normalizedOrNull()
        if (normalizedTitle == null && normalizedDescription == null) {
            return null
        }

        val artist = normalizedDescription
            ?.split("·", "•", "|", "—", " - ")
            ?.firstOrNull()
            ?.normalizedOrNull()

        return DownloadSourceMetadata(
            title = normalizedTitle,
            artist = artist,
            queryHint = listOfNotNull(normalizedTitle, normalizedDescription)
                .joinToString(" ")
                .takeIf { it.isNotBlank() },
        )
    }

    private fun extractHtmlMeta(html: String, property: String): String? {
        val patterns = listOf(
            Regex("""<meta[^>]+property=["']$property["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']$property["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name=["']$property["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+name=["']$property["']""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private fun extractHtmlTag(html: String, tag: String): String? {
        return Regex("""<$tag[^>]*>(.*?)</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.decodeHtml(): String {
        return this
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
