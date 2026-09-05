package com.luxmusic.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Reads the existing on-disk schema without moving or rewriting any audio files. */
internal object LibrarySnapshotCodec {
    data class Decoded(val snapshot: LibrarySnapshot, val skippedRecords: Int)

    fun decode(json: String): Decoded {
        val root = JSONObject(json)
        require(root.opt("tracks") is JSONArray && root.opt("playlists") is JSONArray) {
            "Неверный формат каталога музыки."
        }
        var skipped = 0
        fun <T> records(array: JSONArray, id: (T) -> String, parse: (JSONObject) -> T): List<T> {
            val seen = HashSet<String>()
            return buildList {
                for (index in 0 until array.length()) {
                    val value = runCatching { parse(array.getJSONObject(index)) }.getOrNull()
                    if (value == null || !seen.add(id(value))) skipped++ else add(value)
                }
            }
        }
        val tracks = records(root.getJSONArray("tracks"), Track::id) { row ->
            val path = row.getString("localPath").also { require(it.isNotBlank()) }
            Track(
                id = row.getString("id").also { require(it.isNotBlank()) },
                title = row.text("title") ?: File(path).nameWithoutExtension,
                artist = row.text("artist") ?: "Unknown Artist",
                album = row.text("album") ?: "Singles",
                durationMs = row.optLong("durationMs", 0).coerceAtLeast(0),
                localPath = path,
                artworkPath = row.text("artworkPath"),
                lyrics = row.text("lyrics"),
                sourceUrl = row.text("sourceUrl"),
                importedAt = row.optLong("importedAt", 0),
            )
        }
        val playlists = records(root.getJSONArray("playlists"), Playlist::id) { row ->
            val ids = row.optJSONArray("trackIds")
            Playlist(
                id = row.getString("id").also { require(it.isNotBlank()) },
                name = row.text("name") ?: "Плейлист",
                trackIds = if (ids == null) emptyList() else
                    (0 until ids.length()).mapNotNull { (ids.opt(it) as? String)?.takeIf(String::isNotBlank) }.distinct(),
                createdAt = row.optLong("createdAt", 0),
                artworkPath = row.text("artworkPath"),
            )
        }
        val artworks = root.optJSONObject("artistArtworks")
        val artworkPaths = artworks?.keys()?.asSequence()?.mapNotNull { key ->
            artworks.text(key)?.let { key to it }
        }?.toMap().orEmpty()
        return Decoded(LibrarySnapshot(tracks, playlists, artworkPaths), skipped)
    }

    private fun JSONObject.text(key: String): String? =
        (opt(key) as? String)?.takeIf(String::isNotBlank)
}
