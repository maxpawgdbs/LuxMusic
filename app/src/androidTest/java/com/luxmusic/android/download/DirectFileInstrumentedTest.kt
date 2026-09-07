package com.luxmusic.android.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.LuxMusicApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DirectFileInstrumentedTest {
    @Test(timeout = 30_000)
    fun extensionlessAudioIsImportedWithOneGetAndUnchangedBytes() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        val bytes = wave()
        withServer(bytes, "audio/wav") { url, requests ->
            val downloader = LinkDownloader(app, app.libraryStore)
            val result = downloader.download("$url/stream?signature=test")
            assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
            val tracks = result.getOrThrow()
            try {
                assertEquals(1, tracks.size)
                assertArrayEquals(bytes, java.io.File(tracks.single().localPath).readBytes())
                assertTrue(tracks.single().durationMs >= 1_900)
                assertEquals(1, requests.get())
                assertFalse(downloader.state.value.isRunning)
            } finally { tracks.forEach { app.libraryStore.deleteTrack(it.id) } }
        }
    }

    @Test(timeout = 30_000)
    fun anHtmlResponseWithAnMp3NameFailsWithoutChangingTheLibrary() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        val existingIds = app.libraryStore.snapshot.value.tracks.map { it.id }.toSet()
        withServer("<html>Login required</html>".toByteArray(), "text/html") { url, requests ->
            val downloader = LinkDownloader(app, app.libraryStore)
            assertTrue(downloader.download("$url/fake.mp3").isFailure)
            assertNotNull(downloader.state.value.errorMessage)
            assertFalse(downloader.state.value.isRunning)
            assertEquals(1, requests.get())
            assertEquals(existingIds, app.libraryStore.snapshot.value.tracks.map { it.id }.toSet())
        }
    }

    private suspend fun withServer(body: ByteArray, type: String, test: suspend (String, AtomicInteger) -> Unit) {
        val socket = ServerSocket(0)
        val requests = AtomicInteger()
        val worker = thread(isDaemon = true, name = "direct-file-android-test") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 2_000
                        val input = client.getInputStream().bufferedReader()
                        val request = input.readLine().orEmpty()
                        while (!input.readLine().isNullOrEmpty()) { }
                        requests.incrementAndGet()
                        client.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: $type\r\n" +
                                "Content-Length: ${body.size}\r\n\r\n").toByteArray())
                            if (!request.startsWith("HEAD")) write(body)
                            flush()
                        }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        try { test("http://127.0.0.1:${socket.localPort}", requests) }
        finally { socket.close(); worker.join(2_000) }
    }

    private fun wave(): ByteArray {
        val dataSize = 32_000
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(dataSize + 36); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
        }.array()
    }
}
