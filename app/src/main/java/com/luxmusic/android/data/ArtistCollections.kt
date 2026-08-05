package com.luxmusic.android.data

import java.util.Locale

data class ArtistCollection(
    val name: String,
    val tracks: List<Track>,
    val artworkPath: String?,
) {
    val totalDurationMs: Long = tracks.sumOf(Track::durationMs)
}

object ArtistCollections {
    private const val UNKNOWN_ARTIST = "Неизвестный артист"

    fun build(
        tracks: List<Track>,
        artworkPaths: Map<String, String>,
    ): List<ArtistCollection> {
        val artworkByKey = artworkPaths.entries.associate { (artist, path) ->
            artist.normalizedArtistKey() to path
        }

        return tracks
            .groupBy { it.artist.displayArtist().normalizedArtistKey() }
            .values
            .map { artistTracks ->
                val artistName = artistTracks.first().artist.displayArtist()
                ArtistCollection(
                    name = artistName,
                    tracks = artistTracks,
                    artworkPath = artworkByKey[artistName.normalizedArtistKey()]
                        ?: artistTracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath,
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun matches(track: Track, artistName: String): Boolean {
        return track.artist.displayArtist().normalizedArtistKey() == artistName.normalizedArtistKey()
    }

    fun sameArtist(first: String, second: String): Boolean {
        return first.normalizedArtistKey() == second.normalizedArtistKey()
    }

    private fun String.displayArtist(): String = trim().ifBlank { UNKNOWN_ARTIST }

    private fun String.normalizedArtistKey(): String = displayArtist().lowercase(Locale.ROOT)
}
