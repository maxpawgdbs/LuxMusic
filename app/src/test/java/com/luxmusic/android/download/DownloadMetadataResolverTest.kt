package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DownloadMetadataResolverTest {
    @Test
    fun `spotify metadata comes from oembed`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(
                mapOf(
                    "https://open.spotify.com/oembed?url=https%3A%2F%2Fopen.spotify.com%2Ftrack%2Fabc" to
                        """{"title":"Track Name","author_name":"Artist Name"}""",
                ),
            ),
        )

        val metadata = resolver.resolve(
            url = "https://open.spotify.com/track/abc",
            service = DownloadService.SPOTIFY,
            session = null,
        )

        assertNotNull(metadata)
        assertEquals("Track Name", metadata?.title)
        assertEquals("Artist Name", metadata?.artist)
        assertEquals("Artist Name Track Name", metadata?.queryHint)
    }

    @Test
    fun `apple music metadata comes from lookup api`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(
                mapOf(
                    "https://itunes.apple.com/lookup?id=1712345680&entity=song&country=us" to
                        """{"results":[{"kind":"song","trackName":"Track Name","artistName":"Artist Name","collectionName":"Album Name","trackTimeMillis":215000}]}""",
                ),
            ),
        )

        val metadata = resolver.resolve(
            url = "https://music.apple.com/us/album/song-name/1712345678?i=1712345680",
            service = DownloadService.APPLE_MUSIC,
            session = null,
        )

        assertNotNull(metadata)
        assertEquals("Track Name", metadata?.title)
        assertEquals("Artist Name", metadata?.artist)
        assertEquals("Album Name", metadata?.album)
        assertEquals(215_000L, metadata?.durationMs)
    }

    @Test
    fun `html fallback is used when service has no oembed`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(
                mapOf(
                    "https://music.yandex.ru/album/1/track/2" to """
                        <html>
                        <head>
                        <meta property="og:title" content="Track Name" />
                        <meta property="og:description" content="Artist Name | Album Name" />
                        </head>
                        </html>
                    """.trimIndent(),
                ),
            ),
        )

        val metadata = resolver.resolve(
            url = "https://music.yandex.ru/album/1/track/2",
            service = DownloadService.YANDEX_MUSIC,
            session = null,
        )

        assertNotNull(metadata)
        assertEquals("Track Name", metadata?.title)
        assertEquals("Artist Name", metadata?.artist)
    }

    @Test
    fun `resolver returns null when no source responds`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(emptyMap()),
        )

        val metadata = resolver.resolve(
            url = "https://example.com/track",
            service = DownloadService.UNKNOWN,
            session = null,
        )

        assertNull(metadata)
    }

    private class FakeMetadataBackend : MediaDownloadBackend {
        override fun update(channel: ExtractorChannel) = Unit

        override fun fetchInfo(
            url: String,
            service: DownloadService,
            session: DownloadSession?,
        ): DownloadSourceMetadata? = null

        override fun download(
            requestUrl: String,
            service: DownloadService,
            session: DownloadSession?,
            outputDir: File,
            onProgress: (progress: Float, line: String?) -> Unit,
        ) = Unit
    }

    private class FakeMetadataHttpClient(
        private val responses: Map<String, String>,
    ) : MetadataHttpClient {
        override fun getText(url: String, headers: Map<String, String>): String? {
            return responses[url]
                ?: responses.entries.firstOrNull { (key, _) ->
                    url == key || url.startsWith(key) || key.startsWith(url)
                }?.value
        }
    }
}
