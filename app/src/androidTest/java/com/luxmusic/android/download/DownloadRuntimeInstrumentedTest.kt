package com.luxmusic.android.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.MetadataExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class DownloadRuntimeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun bundledQuickJsRunsOnAndroid() {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        assertTrue("QuickJS is missing from the APK", executable.isFile)
        val process = ProcessBuilder(executable.absolutePath, "-e", "console.log(2 + 2)").redirectErrorStream(true).start()
        try {
            assertTrue("QuickJS timed out", process.waitFor(15, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertEquals("4", output.trim())
        } finally { process.destroy() }
    }

    @Test fun nativeDownloaderAndFfmpegExtractPlayableAudioWithoutInternet() {
        downloadFixture(silentWave(), "audio/wav", "tone.wav")
    }

    @Test(timeout = 90_000)
    fun nativeDownloaderExtractsAudioFromCombinedVideoWithUnknownCodec() {
        val fixtureDir = File(context.cacheDir, "video-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            YtDlpMediaDownloadBackend(context).initialize()
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            val wave = File(fixtureDir, "input.wav").apply { writeBytes(silentWave()) }
            val video = File(fixtureDir, "clip.mp4")
            val log = File(fixtureDir, "ffmpeg.log")
            val builder = ProcessBuilder(
                File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath,
                "-nostdin", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=16x16:r=1",
                "-i", wave.absolutePath, "-t", "2", "-c:v", "mpeg4", "-c:a", "aac", "-y", video.absolutePath,
            ).redirectErrorStream(true).redirectOutput(log)
            val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
            builder.environment()["LD_LIBRARY_PATH"] =
                "${File(packages, "python/usr/lib")}:${File(packages, "ffmpeg/usr/lib")}"
            val process = builder.start()
            try {
                assertTrue("Fixture generation timed out", process.waitFor(30, TimeUnit.SECONDS))
                assertEquals(log.readText(), 0, process.exitValue())
            } finally { process.destroy() }
            downloadFixture(video.readBytes(), "video/mp4", "clip.mp4")
        } finally { fixtureDir.deleteRecursively() }
    }

    private fun downloadFixture(media: ByteArray, contentType: String, name: String) {
        val workspace = File(context.cacheDir, "runtime-test-${UUID.randomUUID()}").apply { mkdirs() }
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true, name = "luxmusic-test-http") {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader()
                        val request = input.readLine().orEmpty()
                        while (!input.readLine().isNullOrEmpty()) { }
                        val output = socket.getOutputStream()
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n" +
                            "Content-Length: ${media.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        if (!request.startsWith("HEAD")) output.write(media)
                        output.flush()
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        try {
            val backend = YtDlpMediaDownloadBackend(context)
            backend.initialize()
            backend.download("http://127.0.0.1:${server.localPort}/$name", DownloadService.UNKNOWN, null, workspace) { _, _ -> }
            val audio = workspace.listFiles().orEmpty().firstOrNull { it.extension in setOf("wav", "m4a", "mp3", "opus") }
            assertNotNull("No audio produced by native downloader", audio)
            assertTrue(MetadataExtractor(context).probeDurationMs(audio!!) >= 1_900)
        } finally {
            server.close()
            worker.join(1_000)
            workspace.deleteRecursively()
        }
    }

    private fun silentWave(): ByteArray {
        val bytes = 8_000 * 2 * 2
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes)
        }.array()
    }
}
