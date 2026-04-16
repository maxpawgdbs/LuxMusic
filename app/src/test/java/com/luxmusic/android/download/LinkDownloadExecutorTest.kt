package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class LinkDownloadExecutorTest {
    @Test
    fun `youtube downloads directly`() = runBlocking {
        val harness = testHarness(
            infoResponses = mapOf(
                "https://youtu.be/demo" to DownloadSourceMetadata(
                    title = "Track Name",
                    artist = "Artist Name",
                    durationMs = 180_000,
                ),
            ),
            downloadOutcomes = mapOf(
                "https://youtu.be/demo" to DownloadOutcome.Success("youtube.mp3"),
            ),
        )

        val result = harness.executor.execute("https://youtu.be/demo", harness::sessionFor, harness::status)

        assertEquals(DownloadAttemptKind.DIRECT, result.finalAttempt.kind)
        assertEquals(DownloadService.YOUTUBE, result.finalAttempt.requestService)
        assertEquals(1, result.tracks.size)
    }

    @Test
    fun `tiktok downloads directly`() = runBlocking {
        val harness = testHarness(
            infoResponses = mapOf(
                "https://www.tiktok.com/@artist/video/1" to DownloadSourceMetadata(
                    title = "Clip Audio",
                    artist = "Artist Name",
                    durationMs = 30_000,
                ),
            ),
            downloadOutcomes = mapOf(
                "https://www.tiktok.com/@artist/video/1" to DownloadOutcome.Success("tiktok.mp3"),
            ),
        )

        val result = harness.executor.execute(
            "https://www.tiktok.com/@artist/video/1",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(DownloadAttemptKind.DIRECT, result.finalAttempt.kind)
        assertEquals(DownloadService.TIKTOK, result.finalAttempt.requestService)
    }

    @Test
    fun `soundcloud downloads directly`() = runBlocking {
        val harness = testHarness(
            infoResponses = mapOf(
                "https://soundcloud.com/artist/track" to DownloadSourceMetadata(
                    title = "Track Name",
                    artist = "Artist Name",
                    durationMs = 180_000,
                ),
            ),
            downloadOutcomes = mapOf(
                "https://soundcloud.com/artist/track" to DownloadOutcome.Success("soundcloud.mp3"),
            ),
        )

        val result = harness.executor.execute(
            "https://soundcloud.com/artist/track",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(DownloadAttemptKind.DIRECT, result.finalAttempt.kind)
        assertEquals(DownloadService.SOUNDCLOUD, result.finalAttempt.requestService)
    }

    @Test
    fun `apple music downloads through matched search`() = runBlocking {
        val harness = testHarness(
            httpResponses = mapOf(
                "https://itunes.apple.com/lookup?id=1712345680&entity=song&country=us" to
                    """{"results":[{"kind":"song","trackName":"Track Name","artistName":"Artist Name","trackTimeMillis":180000}]}""",
            ),
            downloadOutcomes = mapOf(
                "ytsearch1:Artist Name Track Name audio" to DownloadOutcome.Success("apple-fallback.mp3"),
            ),
        )

        val result = harness.executor.execute(
            "https://music.apple.com/us/album/song-name/1712345678?i=1712345680",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, result.finalAttempt.kind)
        assertEquals(DownloadService.YOUTUBE, result.finalAttempt.requestService)
        assertEquals(1, result.tracks.size)
    }

    @Test
    fun `spotify downloads through matched search`() = runBlocking {
        val harness = testHarness(
            httpResponses = mapOf(
                "https://open.spotify.com/oembed?url=https%3A%2F%2Fopen.spotify.com%2Ftrack%2Fabc" to
                    """{"title":"Track Name","author_name":"Artist Name"}""",
            ),
            downloadOutcomes = mapOf(
                "ytsearch1:Artist Name Track Name audio" to DownloadOutcome.Success("spotify-fallback.mp3"),
            ),
        )

        val result = harness.executor.execute(
            "https://open.spotify.com/track/abc",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, result.finalAttempt.kind)
        assertEquals(DownloadService.YOUTUBE, result.finalAttempt.requestService)
    }

    @Test
    fun `yandex music falls back to youtube when direct extractor fails`() = runBlocking {
        val sourceUrl = "https://music.yandex.ru/album/1/track/2"
        val harness = testHarness(
            infoResponses = mapOf(
                sourceUrl to DownloadSourceMetadata(
                    title = "Track Name",
                    artist = "Artist Name",
                    durationMs = 180_000,
                ),
            ),
            downloadOutcomes = mapOf(
                sourceUrl to DownloadOutcome.Failure("direct yandex failed"),
                "ytsearch1:Artist Name Track Name audio" to DownloadOutcome.Success("yandex-fallback.mp3"),
            ),
        )

        val result = harness.executor.execute(sourceUrl, harness::sessionFor, harness::status)

        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, result.finalAttempt.kind)
        assertEquals(DownloadService.YOUTUBE, result.finalAttempt.requestService)
        assertTrue(harness.backend.calls.contains(sourceUrl))
        assertTrue(harness.backend.calls.contains("ytsearch1:Artist Name Track Name audio"))
    }

    @Test
    fun `vk music falls back to youtube when direct extractor fails`() = runBlocking {
        val sourceUrl = "https://vk.com/audio-1_2"
        val harness = testHarness(
            infoResponses = mapOf(
                sourceUrl to DownloadSourceMetadata(
                    title = "Track Name",
                    artist = "Artist Name",
                    durationMs = 180_000,
                ),
            ),
            downloadOutcomes = mapOf(
                sourceUrl to DownloadOutcome.Failure("direct vk failed"),
                "ytsearch1:Artist Name Track Name audio" to DownloadOutcome.Success("vk-fallback.mp3"),
            ),
        )

        val result = harness.executor.execute(sourceUrl, harness::sessionFor, harness::status)

        assertEquals(DownloadAttemptKind.MATCHED_SEARCH, result.finalAttempt.kind)
        assertEquals(DownloadService.YOUTUBE, result.finalAttempt.requestService)
    }

    private fun testHarness(
        infoResponses: Map<String, DownloadSourceMetadata> = emptyMap(),
        httpResponses: Map<String, String> = emptyMap(),
        downloadOutcomes: Map<String, DownloadOutcome>,
    ): TestHarness {
        val backend = FakeDownloadBackend(infoResponses, downloadOutcomes)
        val resolver = CompositeDownloadMetadataResolver(
            backend = backend,
            httpClient = FakeMetadataHttpClient(httpResponses),
        )
        val importer = FakeImporter()
        val audioInspector = FakeAudioInspector()
        val workspaceManager = TempWorkspaceManager()

        return TestHarness(
            executor = LinkDownloadExecutor(
                planner = DownloadPlanner(),
                metadataResolver = resolver,
                backend = backend,
                importer = importer,
                audioInspector = audioInspector,
                workspaceManager = workspaceManager,
            ),
            backend = backend,
        )
    }

    private class TestHarness(
        val executor: LinkDownloadExecutor,
        val backend: FakeDownloadBackend,
    ) {
        fun sessionFor(service: DownloadService): DownloadSession? = when (service) {
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
            -> null
            else -> null
        }

        fun status(progress: Float, message: String) = Unit
    }

    private sealed class DownloadOutcome {
        data class Success(val fileName: String) : DownloadOutcome()

        data class Failure(val message: String) : DownloadOutcome()
    }

    private class FakeDownloadBackend(
        private val infoResponses: Map<String, DownloadSourceMetadata>,
        private val downloadOutcomes: Map<String, DownloadOutcome>,
    ) : MediaDownloadBackend {
        val calls = mutableListOf<String>()

        override fun update(channel: ExtractorChannel) = Unit

        override fun fetchInfo(
            url: String,
            service: DownloadService,
            session: DownloadSession?,
        ): DownloadSourceMetadata? = infoResponses[url]

        override fun download(
            requestUrl: String,
            service: DownloadService,
            session: DownloadSession?,
            outputDir: File,
            onProgress: (progress: Float, line: String?) -> Unit,
        ) {
            calls += requestUrl
            when (val outcome = downloadOutcomes[requestUrl]) {
                is DownloadOutcome.Success -> {
                    onProgress(50f, "downloading")
                    File(outputDir, outcome.fileName).writeBytes(ByteArray(128 * 1_024) { 1 })
                    onProgress(100f, "done")
                }

                is DownloadOutcome.Failure -> error(outcome.message)
                null -> error("unexpected request: $requestUrl")
            }
        }
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

    private class FakeImporter : DownloadedTrackImporter {
        override suspend fun importDownloadedFiles(
            audioFiles: List<File>,
            sourceUrl: String?,
            companionResolver: (File) -> List<File>,
        ): List<Track> {
            return audioFiles.map { file ->
                Track(
                    id = UUID.randomUUID().toString(),
                    title = file.nameWithoutExtension,
                    artist = "Artist Name",
                    album = "Singles",
                    durationMs = 180_000,
                    localPath = file.absolutePath,
                    sourceUrl = sourceUrl,
                    importedAt = 1L,
                )
            }
        }
    }

    private class FakeAudioInspector : DownloadAudioInspector {
        override fun probeDurationMs(file: File): Long = 180_000L
    }

    private class TempWorkspaceManager : DownloadWorkspaceManager {
        override fun createWorkspace(prefix: String): File {
            return Files.createTempDirectory("luxmusic-$prefix").toFile()
        }

        override fun cleanup(workspace: File) {
            workspace.deleteRecursively()
        }
    }
}
