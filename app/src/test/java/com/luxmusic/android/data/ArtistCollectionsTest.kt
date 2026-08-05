package com.luxmusic.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistCollectionsTest {
    @Test
    fun buildGroupsArtistsIgnoringWhitespaceAndCase() {
        val groups = ArtistCollections.build(
            tracks = listOf(track("1", " Aurora "), track("2", "aurora"), track("3", "Баста")),
            artworkPaths = emptyMap(),
        )

        assertEquals(listOf("Aurora", "Баста"), groups.map(ArtistCollection::name))
        assertEquals(listOf("1", "2"), groups.first().tracks.map(Track::id))
    }

    @Test
    fun buildUsesArtistArtworkAndCalculatesDuration() {
        val groups = ArtistCollections.build(
            tracks = listOf(track("1", "Aurora", durationMs = 60_000), track("2", "AURORA", durationMs = 90_000)),
            artworkPaths = mapOf("aurora" to "/covers/artist.jpg"),
        )

        assertEquals("/covers/artist.jpg", groups.single().artworkPath)
        assertEquals(150_000L, groups.single().totalDurationMs)
    }

    @Test
    fun matchesUsesSameDynamicGroupingRules() {
        assertTrue(ArtistCollections.matches(track("1", "  Aurora"), "aurora"))
        assertTrue(ArtistCollections.sameArtist(" ", "Неизвестный артист"))
    }

    private fun track(
        id: String,
        artist: String,
        durationMs: Long = 1_000,
    ) = Track(
        id = id,
        title = "Track $id",
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        localPath = "/music/$id.mp3",
        importedAt = 1L,
    )
}
