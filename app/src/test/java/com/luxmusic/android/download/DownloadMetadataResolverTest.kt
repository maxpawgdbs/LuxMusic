package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DownloadMetadataResolverTest {
    @Test
    fun `youtube metadata falls back to oembed when extractor info is missing`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(
                mapOf(
                    "https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fyoutu.be%2Fdemo" to
                        """{"title":"Track Name","author_name":"Artist Name"}""",
                ),
            ),
        )

        val metadata = resolver.resolve(
            url = "https://youtu.be/demo",
            service = DownloadService.YOUTUBE,
            session = null,
        )

        assertNotNull(metadata)
        assertEquals("Track Name", metadata?.title)
        assertEquals("Artist Name", metadata?.artist)
    }

    @Test
    fun `soundcloud metadata can fall back to html`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(
                mapOf(
                    "https://soundcloud.com/artist/track" to """
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
            url = "https://soundcloud.com/artist/track",
            service = DownloadService.SOUNDCLOUD,
            session = null,
        )

        assertNotNull(metadata)
        assertEquals("Track Name", metadata?.title)
        assertEquals("Artist Name", metadata?.artist)
    }

    @Test
    fun `unsupported services are ignored`() {
        val resolver = CompositeDownloadMetadataResolver(
            backend = FakeMetadataBackend(),
            httpClient = FakeMetadataHttpClient(emptyMap()),
        )

        val metadata = resolver.resolve(
            url = "https://open.spotify.com/track/abc",
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
