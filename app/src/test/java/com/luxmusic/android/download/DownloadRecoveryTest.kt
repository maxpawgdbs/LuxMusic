package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DownloadRecoveryTest {
    @Test fun `stalled update is interrupted and tiktok fallback still runs`() = runBlocking {
        val harness = Harness(downloadError = IOException("Extractor unavailable"), fallbackEnabled = true,
            updateDelayMs = 60_000, updateTimeoutMs = 50)
        val result = harness.execute("https://vm.tiktok.com/clip/")
        assertEquals(1, result.tracks.size)
        assertEquals(1, harness.downloads)
        assertEquals(1, harness.updates)
        assertEquals(1, harness.fallbacks)
        assertTrue(harness.workspaces.none(File::exists))
    }

    @Test fun `cancelled extractor never updates retries or imports`() = runBlocking {
        val harness = Harness(downloadError = CancellationException("cancel"))
        val error = runCatching { harness.execute() }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, harness.downloads)
        assertEquals(0, harness.updates)
        assertEquals(0, harness.imports)
        assertTrue(harness.workspaces.none(File::exists))
    }

    @Test fun `disk failure during import does not redownload or retry`() = runBlocking {
        val harness = Harness(importError = IOException("No space left on device"))
        assertNotNull(runCatching { harness.execute() }.exceptionOrNull())
        assertEquals(1, harness.downloads)
        assertEquals(0, harness.updates)
        assertEquals(1, harness.imports)
        assertTrue(harness.workspaces.none(File::exists))
    }

    @Test fun `large invalid audio is rejected even when extension looks valid`() = runBlocking {
        val harness = Harness(durationMs = 0)
        assertNotNull(runCatching { harness.execute() }.exceptionOrNull())
        assertEquals(0, harness.imports)
    }

    @Test fun `non finite progress cannot reach compose`() = runBlocking {
        val harness = Harness()
        harness.execute()
        assertTrue(harness.progress.isNotEmpty())
        assertTrue(harness.progress.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun `tiktok fallback runs after extractor retry and retains original source`() = runBlocking {
        val harness = Harness(downloadError = IOException("Extractor unavailable"), fallbackEnabled = true)
        val source = "https://vm.tiktok.com/clip/"
        val result = harness.execute(source)
        assertEquals(2, harness.downloads)
        assertEquals(1, harness.updates)
        assertEquals(1, harness.fallbacks)
        assertEquals(source, result.tracks.single().sourceUrl)
        assertTrue(harness.workspaces.none(File::exists))
    }

    private class Harness(
        private val downloadError: Exception? = null,
        private val importError: Exception? = null,
        private val durationMs: Long = 30_000,
        fallbackEnabled: Boolean = false,
        private val updateDelayMs: Long = 0,
        updateTimeoutMs: Long = 45_000,
    ) {
        var downloads = 0
        var updates = 0
        var imports = 0
        var fallbacks = 0
        val workspaces = mutableListOf<File>()
        val progress = mutableListOf<Float>()
        private val backend = object : MediaDownloadBackend {
            override fun update(channel: ExtractorChannel) { updates++; if (updateDelayMs > 0) Thread.sleep(updateDelayMs) }
            override fun fetchInfo(url: String, service: DownloadService, session: DownloadSession?) = null
            override fun download(requestUrl: String, service: DownloadService, session: DownloadSession?, outputDir: File,
                onProgress: (Float, String?) -> Unit) {
                downloads++
                downloadError?.let { throw it }
                File(outputDir, "audio.m4a").writeBytes(ByteArray(200_000))
                onProgress(Float.NaN, null)
            }
        }
        private val fallback = object : MediaDownloadBackend {
            override fun update(channel: ExtractorChannel) = Unit
            override fun fetchInfo(url: String, service: DownloadService, session: DownloadSession?) = null
            override fun download(requestUrl: String, service: DownloadService, session: DownloadSession?, outputDir: File,
                onProgress: (Float, String?) -> Unit) {
                fallbacks++
                assertNull(session)
                File(outputDir, "audio.m4a").writeBytes(ByteArray(200_000))
            }
        }
        private val executor = LinkDownloadExecutor(
            DownloadPlanner(),
            CompositeDownloadMetadataResolver(backend, object : MetadataHttpClient {
                override fun getText(url: String, headers: Map<String, String>) = null
            }),
            backend,
            object : DownloadedTrackImporter {
                override suspend fun importDownloadedFiles(audioFiles: List<File>, sourceUrl: String?,
                    companionResolver: (File) -> List<File>): List<Track> {
                    imports++
                    importError?.let { throw it }
                    return listOf(Track("id", "Title", "Artist", "Album", durationMs,
                        "/library/audio.m4a", sourceUrl = sourceUrl, importedAt = 0))
                }
            },
            object : DownloadAudioInspector { override fun probeDurationMs(file: File) = durationMs },
            object : DownloadWorkspaceManager {
                override fun createWorkspace(prefix: String): File =
                    Files.createTempDirectory("luxmusic-test-").toFile().also(workspaces::add)
                override fun cleanup(workspace: File) { workspace.deleteRecursively() }
            },
            if (fallbackEnabled) fallback else null,
            updateTimeoutMs,
        )
        suspend fun execute(url: String = "https://youtu.be/test") =
            executor.execute(url, { null }) { value, _ -> progress.add(value); Unit }
    }
}
