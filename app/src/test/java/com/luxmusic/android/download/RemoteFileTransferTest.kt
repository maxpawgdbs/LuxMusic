package com.luxmusic.android.download

import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class RemoteFileTransferTest {
    @Test fun `extensionless audio uses one GET and preserves exact bytes`() = fixture { server, directory ->
        val bytes = wave(200_000)
        server.response = Response(bytes, headers = mapOf("Content-Type" to "audio/wav"))
        val result = RemoteFileTransfer().downloadIfMedia(server.url("/download?signature=abc"), directory) {}!!
        assertEquals(RemoteFileKind.AUDIO, result.kind)
        assertEquals("wav", result.file.extension)
        assertArrayEquals(bytes, result.file.readBytes())
        assertEquals(1, server.requests.get())
        assertEquals("GET", server.method)
    }

    @Test fun `unknown binary content is recognized by its signature`() = fixture { server, directory ->
        server.response = Response(wave(128), headers = mapOf("Content-Type" to "application/octet-stream"))
        assertEquals("wav", RemoteFileTransfer().downloadIfMedia(server.url(), directory) {}!!.file.extension)
    }

    @Test fun `zip is detected and transferred without a second request`() = fixture { server, directory ->
        val bytes = byteArrayOf(0x50, 0x4b, 3, 4) + ByteArray(100)
        server.response = Response(bytes)
        assertEquals(RemoteFileKind.ZIP, RemoteFileTransfer().downloadIfMedia(server.url(), directory) {}!!.kind)
        assertEquals(1, server.requests.get())
    }

    @Test fun `html with an audio filename is not treated as a track`() = fixture { server, directory ->
        server.response = Response("<html>Please sign in</html>".toByteArray(), headers = mapOf("Content-Type" to "text/html"))
        assertNull(RemoteFileTransfer().downloadIfMedia(server.url("/fake.mp3"), directory) {})
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `rejects truncated content and removes partial files`() = fixture { server, directory ->
        server.response = Response(wave(128), declaredLength = 500)
        assertNotNull(runCatching { RemoteFileTransfer().downloadIfMedia(server.url(), directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `rejects declared oversized content before saving`() = fixture { server, directory ->
        server.response = Response(wave(128), declaredLength = 10_000)
        assertNotNull(runCatching { RemoteFileTransfer(maximumBytes = 1_000).downloadIfMedia(server.url(), directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `bounds unknown length streams and removes partial files`() = fixture { server, directory ->
        server.response = Response(wave(2_000), declaredLength = null)
        assertNotNull(runCatching { RemoteFileTransfer(maximumBytes = 1_000).downloadIfMedia(server.url(), directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `relative redirects work without HEAD probes`() = fixture { server, directory ->
        server.response = Response(wave(128))
        server.redirect = "/file"
        assertNotNull(RemoteFileTransfer().downloadIfMedia(server.url("/redirect"), directory) {})
        assertEquals(2, server.requests.get())
    }

    @Test fun `redirect loops are bounded`() = fixture { server, directory ->
        server.redirect = "/redirect"
        assertNotNull(runCatching { RemoteFileTransfer().downloadIfMedia(server.url("/redirect"), directory) {} }.exceptionOrNull())
        assertEquals(6, server.requests.get())
    }

    @Test fun `redirects to non HTTP protocols are rejected`() = fixture { server, directory ->
        server.redirect = "file:///unrelated-file"
        assertNotNull(runCatching { RemoteFileTransfer().downloadIfMedia(server.url("/redirect"), directory) {} }.exceptionOrNull())
    }

    @Test fun `non successful HTTP responses cannot be imported`() = fixture { server, directory ->
        server.response = Response(wave(128), status = 403)
        val error = runCatching { RemoteFileTransfer().downloadIfMedia(server.url(), directory) {} }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("403"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `unsolicited partial responses cannot masquerade as complete tracks`() = fixture { server, directory ->
        server.response = Response(wave(128), status = 206)
        assertNotNull(runCatching { RemoteFileTransfer().downloadIfMedia(server.url(), directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `URL credentials are rejected before sending a request`() = fixture { server, directory ->
        val url = server.url().replace("http://", "http://user:password@")
        assertTrue(runCatching { RemoteFileTransfer().downloadIfMedia(url, directory) {} }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requests.get())
    }

    @Test fun `cancellation removes the partial file`() = fixture { server, directory ->
        server.response = Response(wave(500_000))
        try {
            val error = runCatching {
                RemoteFileTransfer().downloadIfMedia(server.url(), directory) { Thread.currentThread().interrupt() }
            }.exceptionOrNull()
            assertTrue(error is InterruptedException)
        } finally { Thread.interrupted() }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `content disposition names cannot escape the workspace`() = fixture { server, directory ->
        server.response = Response(wave(128), headers = mapOf("Content-Disposition" to "attachment; filename=\"../../audio.wav\""))
        val result = RemoteFileTransfer().downloadIfMedia(server.url(), directory) {}!!
        assertEquals(directory.canonicalFile, result.file.canonicalFile.parentFile)
        assertEquals("audio.wav", result.file.name)
    }

    @Test fun `utf8 names and literal plus signs survive`() {
        assertEquals("Песня.wav", RemoteFileTransfer.responseFileName("attachment; filename*=UTF-8''%D0%9F%D0%B5%D1%81%D0%BD%D1%8F.wav", URL("https://example.com/download")))
        assertEquals("a+b.wav", RemoteFileTransfer.responseFileName(null, URL("https://example.com/a+b.wav")))
        assertEquals("a+b.wav", RemoteFileTransfer.responseFileName("filename*=UTF-8''a+b.wav", URL("https://example.com/download")))
    }

    @Test fun `combined video is not mislabeled as an audio only container`() {
        assertNull(RemoteFileTransfer.audioExtension(byteArrayOf(0,0,0,24) + "ftypisom".toByteArray() + ByteArray(12)))
    }

    private fun wave(size: Int): ByteArray = ByteArray(size).apply {
        "RIFF".toByteArray().copyInto(this)
        "WAVE".toByteArray().copyInto(this, 8)
    }

    private fun fixture(test: (Server, File) -> Unit) {
        val directory = Files.createTempDirectory("luxmusic-transfer-test-").toFile()
        val server = Server()
        try { test(server, directory) } finally { server.close(); directory.deleteRecursively() }
    }

    private data class Response(val body: ByteArray, val status: Int = 200,
        val headers: Map<String, String> = emptyMap(), val declaredLength: Long? = body.size.toLong())

    private class Server {
        private val socket = ServerSocket(0)
        val requests = AtomicInteger()
        @Volatile var method = ""
        @Volatile var response = Response(ByteArray(0))
        @Volatile var redirect: String? = null
        private val worker = thread(isDaemon = true, name = "remote-file-test-server") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 2_000
                        val input = client.getInputStream().bufferedReader()
                        val request = input.readLine().orEmpty()
                        method = request.substringBefore(' ')
                        while (!input.readLine().isNullOrEmpty()) { }
                        requests.incrementAndGet()
                        val current = if (request.contains(" /redirect ") && redirect != null)
                            Response(ByteArray(0), 302, mapOf("Location" to redirect!!)) else response
                        val headers = buildString {
                            append("HTTP/1.1 ${current.status} Test\r\nConnection: close\r\n")
                            current.declaredLength?.let { append("Content-Length: $it\r\n") }
                            current.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                            append("\r\n")
                        }
                        client.getOutputStream().apply { write(headers.toByteArray()); write(current.body); flush() }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        fun url(path: String = "/file"): String = "http://127.0.0.1:${socket.localPort}$path"
        fun close() { socket.close(); worker.join(2_000) }
    }
}
