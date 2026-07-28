package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        assertEquals(0, harness.backend.fetchInfoCalls)
        assertEquals(0, harness.backend.updateCalls)
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
    fun `generic yt dlp site downloads directly`() = runBlocking {
        val harness = testHarness(
            downloadOutcomes = mapOf(
                "https://bandcamp.com/track/abc" to DownloadOutcome.Success("generic.m4a"),
            ),
        )

        val result = harness.executor.execute(
            "bandcamp.com/track/abc",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(DownloadAttemptKind.DIRECT, result.finalAttempt.kind)
        assertEquals(DownloadService.UNKNOWN, result.finalAttempt.requestService)
    }

    @Test
    fun `failed direct download updates extractor and retries once`() = runBlocking {
        val harness = testHarness(
            downloadOutcomes = mapOf(
                "https://youtu.be/retry" to DownloadOutcome.SuccessAfterFailure("retry.m4a"),
            ),
        )

        val result = harness.executor.execute(
            "https://youtu.be/retry",
            harness::sessionFor,
            harness::status,
        )

        assertEquals(1, result.tracks.size)
        assertEquals(1, harness.backend.updateCalls)
    }

    @Test
    fun `spotify resolves metadata then downloads a matched result`() = runBlocking {
        val harness = testHarness(
            httpResponses = mapOf(
                "https://open.spotify.com/oembed" to
                    """{"title":"Track Name","author_name":"Artist Name"}""",
            ),
            downloadOutcomes = mapOf(
                "ytsearch1:Artist Name Track Name audio" to DownloadOutcome.Success("matched.m4a"),
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
        fun sessionFor(service: DownloadService): DownloadSession? = null

        fun status(progress: Float, message: String) = Unit
    }

    private sealed class DownloadOutcome {
        data class Success(val fileName: String) : DownloadOutcome()
        data class SuccessAfterFailure(val fileName: String) : DownloadOutcome()
    }

    private class FakeDownloadBackend(
        private val infoResponses: Map<String, DownloadSourceMetadata>,
        private val downloadOutcomes: Map<String, DownloadOutcome>,
    ) : MediaDownloadBackend {
        private val downloadAttempts = mutableMapOf<String, Int>()
        var fetchInfoCalls = 0
        var updateCalls = 0

        override fun update(channel: ExtractorChannel) {
            updateCalls++
        }

        override fun fetchInfo(
            url: String,
            service: DownloadService,
            session: DownloadSession?,
        ): DownloadSourceMetadata? {
            fetchInfoCalls++
            return infoResponses[url]
        }

        override fun download(
            requestUrl: String,
            service: DownloadService,
            session: DownloadSession?,
            outputDir: File,
            onProgress: (progress: Float, line: String?) -> Unit,
        ) {
            val attemptNumber = downloadAttempts.getOrDefault(requestUrl, 0) + 1
            downloadAttempts[requestUrl] = attemptNumber
            when (val outcome = downloadOutcomes[requestUrl]) {
                is DownloadOutcome.Success -> {
                    writeSuccess(outputDir, outcome.fileName, onProgress)
                }

                is DownloadOutcome.SuccessAfterFailure -> {
                    if (attemptNumber == 1) {
                        error("outdated extractor")
                    }
                    writeSuccess(outputDir, outcome.fileName, onProgress)
                }

                null -> error("unexpected request: $requestUrl")
            }
        }

        private fun writeSuccess(
            outputDir: File,
            fileName: String,
            onProgress: (progress: Float, line: String?) -> Unit,
        ) {
            onProgress(50f, "downloading")
            File(outputDir, fileName).writeBytes(ByteArray(128 * 1_024) { 1 })
            onProgress(100f, "done")
        }
    }

    private class FakeMetadataHttpClient(
        private val responses: Map<String, String>,
    ) : MetadataHttpClient {
        override fun getText(url: String, headers: Map<String, String>): String? {
            return responses.entries.firstOrNull { (prefix, _) -> url.startsWith(prefix) }?.value
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
