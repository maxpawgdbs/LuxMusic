package com.luxmusic.android.data

import org.junit.Assert.*
import org.junit.Test

class LibrarySnapshotCodecTest {
    private val legacyTrack = """{"id":"old-id","title":"Музыка","artist":"Артист","album":"Альбом",
        "durationMs":180000,"localPath":"/data/user/0/com.luxmusic.android/files/luxmusic/tracks/old-id.m4a",
        "artworkPath":"/old/cover.jpg","lyrics":"Текст","sourceUrl":"https://youtu.be/old","importedAt":42}"""

    @Test fun `version 06 paths ids metadata and playlists survive unchanged`() {
        val decoded = LibrarySnapshotCodec.decode("""{"tracks":[$legacyTrack],"playlists":[
            {"id":"playlist-id","name":"Избранное","trackIds":["old-id"],"createdAt":12,"artworkPath":"/playlist.jpg"}],
            "artistArtworks":{"Артист":"/artist.jpg"}}""")
        val track = decoded.snapshot.tracks.single()
        assertEquals("old-id", track.id)
        assertEquals("/data/user/0/com.luxmusic.android/files/luxmusic/tracks/old-id.m4a", track.localPath)
        assertEquals("Музыка", track.title)
        assertEquals("Текст", track.lyrics)
        assertEquals("/old/cover.jpg", track.artworkPath)
        assertEquals(180000, track.durationMs)
        assertEquals(listOf("old-id"), decoded.snapshot.playlists.single().trackIds)
        assertEquals("/artist.jpg", decoded.snapshot.artistArtworkPaths["Артист"])
        assertEquals(0, decoded.skippedRecords)
    }

    @Test fun `one corrupt row does not hide readable tracks`() {
        val decoded = LibrarySnapshotCodec.decode("""{"tracks":[{},$legacyTrack],"playlists":[]}""")
        assertEquals("old-id", decoded.snapshot.tracks.single().id)
        assertEquals(1, decoded.skippedRecords)
    }

    @Test fun `duplicate ids cannot crash compose lazy list keys`() {
        val decoded = LibrarySnapshotCodec.decode("""{"tracks":[$legacyTrack,$legacyTrack],"playlists":[]}""")
        assertEquals(1, decoded.snapshot.tracks.size)
        assertEquals(1, decoded.skippedRecords)
    }

    @Test fun `missing optional legacy metadata has defaults`() {
        val decoded = LibrarySnapshotCodec.decode("""{"tracks":[{"id":"a","localPath":"/song.mp3"}],"playlists":[]}""")
        assertEquals("song", decoded.snapshot.tracks.single().title)
        assertEquals(0, decoded.skippedRecords)
    }

    @Test fun `invalid manifest is never treated as valid empty collection`() {
        listOf("{", "{}", "[]", """{"tracks":"broken","playlists":[]}""").forEach {
            assertNotNull(runCatching { LibrarySnapshotCodec.decode(it) }.exceptionOrNull())
        }
    }
}
