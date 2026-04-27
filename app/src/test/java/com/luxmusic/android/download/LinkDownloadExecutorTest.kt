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
    fun `unsupported service returns no plan error`() = runBlocking {
        val harness = testHarness(
            downloadOutcomes = emptyMap(),
        )

        val failure = runCatching {
            harness.executor.execute("https://open.spotify.com/track/abc", harness::sessionFor, harness::status)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("YouTube, TikTok и SoundCloud"))
    }

    private fun testHarness(
        infoResponses: Map<String, DownloadSourceMetadata> = emptyMap(),
        downloadOutcomes: Map<String, DownloadOutcome>,
    ): TestHarness {
        val backend = FakeDownloadBackend(infoResponses, downloadOutcomes)
        val resolver = CompositeDownloadMetadataResolver(
            backend = backend,
            httpClient = FakeMetadataHttpClient(),
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
        )
    }

    private class TestHarness(
        val executor: LinkDownloadExecutor,
    ) {
        fun sessionFor(service: DownloadService): DownloadSession? = null

        fun status(progress: Float, message: String) = Unit
    }

    private sealed class DownloadOutcome {
        data class Success(val fileName: String) : DownloadOutcome()
    }

    private class FakeDownloadBackend(
        private val infoResponses: Map<String, DownloadSourceMetadata>,
        private val downloadOutcomes: Map<String, DownloadOutcome>,
    ) : MediaDownloadBackend {
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
            when (val outcome = downloadOutcomes[requestUrl]) {
                is DownloadOutcome.Success -> {
                    onProgress(50f, "downloading")
                    File(outputDir, outcome.fileName).writeBytes(ByteArray(128 * 1_024) { 1 })
                    onProgress(100f, "done")
                }

                null -> error("unexpected request: $requestUrl")
            }
        }
    }

    private class FakeMetadataHttpClient : MetadataHttpClient {
        override fun getText(url: String, headers: Map<String, String>): String? = null
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
