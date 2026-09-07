package com.luxmusic.android.download

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Duration

internal object CatalogMetadataParser {
    fun fromDeezer(response: String): DownloadSourceMetadata? = runCatching {
        val root = JSONObject(response)
        if (root.has("error")) return null
        val title = root.optString("title").takeIf(String::isNotBlank) ?: return null
        val artist = root.optJSONObject("artist")?.optString("name")?.takeIf(String::isNotBlank) ?: return null
        DownloadSourceMetadata(title, artist, root.optJSONObject("album")?.optString("title"),
            root.optLong("duration").takeIf { it > 0 }?.times(1_000), "$artist $title")
    }.getOrNull()

    fun fromHtml(html: String): DownloadSourceMetadata? {
        val recordings = mutableListOf<JSONObject>()
        fun visit(value: Any?, depth: Int) {
            if (depth > 8 || recordings.size > 1) return
            when (value) {
                is JSONArray -> (0 until minOf(value.length(), 64)).forEach { visit(value.opt(it), depth + 1) }
                is JSONObject -> {
                    val type = value.opt("@type")
                    val recording = type == "MusicRecording" || (type is JSONArray &&
                        (0 until minOf(type.length(), 16)).any { type.optString(it) == "MusicRecording" })
                    if (recording) recordings += value
                    else visit(value.opt("@graph"), depth + 1)
                }
            }
        }
        Regex("""<script\b[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).take(16).forEach {
            runCatching { visit(JSONTokener(it.groupValues[1]).nextValue(), 0) }
        }
        val song = recordings.singleOrNull() ?: return null
        val title = song.optString("name").takeIf(String::isNotBlank) ?: return null
        val artists = when (val byArtist = song.opt("byArtist") ?: song.opt("creator")) {
            is JSONArray -> (0 until minOf(byArtist.length(), 16)).mapNotNull { byArtist.optJSONObject(it)?.optString("name") }
            is JSONObject -> listOf(byArtist.optString("name"))
            is String -> listOf(byArtist)
            else -> emptyList()
        }.filter(String::isNotBlank)
        val artist = artists.joinToString(", ").takeIf(String::isNotBlank) ?: return null
        val duration = runCatching { Duration.parse(song.optString("duration")).toMillis() }.getOrNull()
        return DownloadSourceMetadata(title, artist, song.optJSONObject("inAlbum")?.optString("name"),
            duration?.takeIf { it > 0 }, "$artist $title")
    }
}
