package com.luxmusic.android.download.yandex

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YandexCatalogResolverTest {
    @Test
    fun resolvesSingleTrackWithItsAlbum() = runBlocking {
        val api = FakeApi(
            tracks = mapOf(7L to track(7L, albumId = 11L, album = "Первый")),
        )

        val result = YandexCatalogResolver(api).resolve(
            YandexMusicSource(YandexSourceKind.TRACK, 7L),
        )

        assertEquals("Трек 7", result.label)
        assertEquals(listOf(7L), result.tracks.map(YandexTrack::id))
        assertEquals("Первый", result.albums.single().title)
    }

    @Test
    fun albumFiltersUnavailableTracksAndDeduplicatesIds() = runBlocking {
        val album = YandexAlbum(
            id = 3L,
            title = "Альбом",
            type = "album",
            artistIds = listOf(1L),
            tracks = listOf(track(1L), track(1L), track(2L, available = false), track(3L)),
        )
        val result = YandexCatalogResolver(FakeApi(albums = mapOf(3L to album))).resolve(
            YandexMusicSource(YandexSourceKind.ALBUM, 3L),
        )

        assertEquals(listOf(1L, 3L), result.tracks.map(YandexTrack::id))
        assertEquals(listOf(1L, 3L), result.albums.single().tracks.map(YandexTrack::id))
        assertEquals(listOf("Альбом", "Альбом"), result.albums.single().tracks.map(YandexTrack::albumTitle))
    }

    @Test
    fun artistPaginatesFiltersAndKeepsAlbumGroups() = runBlocking {
        val api = FakeApi(
            artists = mapOf(9L to YandexArtist(9L, "Исполнитель")),
            pages = mapOf(
                0 to YandexAlbumPage(
                    albums = listOf(
                        album(10L, "Альбом A", "album", 9L),
                        album(11L, "Чужой", "album", 8L),
                        album(12L, "Сборник", "compilation", 9L),
                    ),
                    page = 0,
                    perPage = 3,
                    total = 4,
                ),
                1 to YandexAlbumPage(
                    albums = listOf(album(13L, "EP B", "ep", 9L), album(10L, "Дубликат", "album", 9L)),
                    page = 1,
                    perPage = 3,
                    total = 4,
                ),
            ),
            albums = mapOf(
                10L to album(10L, "Альбом A", "album", 9L, listOf(track(1L), track(2L))),
                13L to album(13L, "EP B", "ep", 9L, listOf(track(2L), track(3L))),
            ),
        )

        val result = YandexCatalogResolver(api).resolve(
            YandexMusicSource(YandexSourceKind.ARTIST, 9L),
        )

        assertEquals(listOf("Альбом A", "EP B"), result.albums.map(YandexResolvedAlbum::title))
        assertEquals(listOf(1L, 2L, 3L), result.tracks.map(YandexTrack::id))
        assertEquals(listOf(2L, 3L), result.albums[1].tracks.map(YandexTrack::id))
        assertEquals(listOf("EP B", "EP B"), result.albums[1].tracks.map(YandexTrack::albumTitle))
        assertEquals(listOf(0, 1), api.requestedPages)
    }

    @Test
    fun artistStopsWhenPagerIsComplete() = runBlocking {
        val api = FakeApi(
            artists = mapOf(9L to YandexArtist(9L, "Исполнитель")),
            pages = mapOf(
                0 to YandexAlbumPage(
                    albums = listOf(album(10L, "Альбом", "single", 9L)),
                    page = 0,
                    perPage = 50,
                    total = 1,
                ),
            ),
            albums = mapOf(10L to album(10L, "Альбом", "single", 9L, listOf(track(1L)))),
        )

        YandexCatalogResolver(api).resolve(YandexMusicSource(YandexSourceKind.ARTIST, 9L))

        assertEquals(listOf(0), api.requestedPages)
    }

    @Test
    fun enforcesCollectionTrackLimitAcrossArtistAlbums() {
        val api = FakeApi(
            artists = mapOf(9L to YandexArtist(9L, "Исполнитель")),
            pages = mapOf(
                0 to YandexAlbumPage(
                    albums = listOf(album(10L, "A", "album", 9L), album(11L, "B", "album", 9L)),
                    page = 0,
                    perPage = 50,
                    total = 2,
                ),
            ),
            albums = mapOf(
                10L to album(10L, "A", "album", 9L, listOf(track(1L), track(2L))),
                11L to album(11L, "B", "album", 9L, listOf(track(3L))),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                YandexCatalogResolver(api, maxTracks = 2).resolve(
                    YandexMusicSource(YandexSourceKind.ARTIST, 9L),
                )
            }
        }
    }

    private class FakeApi(
        private val tracks: Map<Long, YandexTrack> = emptyMap(),
        private val albums: Map<Long, YandexAlbum> = emptyMap(),
        private val artists: Map<Long, YandexArtist> = emptyMap(),
        private val pages: Map<Int, YandexAlbumPage> = emptyMap(),
    ) : YandexCatalogApi {
        val requestedPages = mutableListOf<Int>()

        override suspend fun track(id: Long): YandexTrack? = tracks[id]
        override suspend fun albumWithTracks(id: Long): YandexAlbum? = albums[id]
        override suspend fun artist(id: Long): YandexArtist? = artists[id]
        override suspend fun directAlbums(artistId: Long, page: Int, pageSize: Int): YandexAlbumPage? {
            requestedPages += page
            return pages[page]
        }
    }

    companion object {
        private fun track(
            id: Long,
            albumId: Long? = null,
            album: String? = null,
            available: Boolean = true,
        ) = YandexTrack(
            id = id,
            title = "Трек $id",
            artists = listOf(YandexArtist(9L, "Исполнитель")),
            albumId = albumId,
            albumTitle = album,
            durationMs = 180_000L,
            coverUri = null,
            available = available,
        )

        private fun album(
            id: Long,
            title: String,
            type: String,
            artistId: Long,
            tracks: List<YandexTrack> = emptyList(),
        ) = YandexAlbum(id, title, type, listOf(artistId), tracks)
    }
}
