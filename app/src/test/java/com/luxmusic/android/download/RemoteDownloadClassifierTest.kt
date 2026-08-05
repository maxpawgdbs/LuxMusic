package com.luxmusic.android.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDownloadClassifierTest {
    @Test
    fun `detects zip by url without trusting query text`() {
        assertTrue(RemoteDownloadClassifier.isArchiveUrl("https://example.test/music.ZIP?token=1"))
        assertFalse(RemoteDownloadClassifier.isArchiveUrl("https://example.test/download?name=music.zip"))
        assertFalse(RemoteDownloadClassifier.isArchiveUrl("https://example.test/song.mp3?next=.zip"))
    }

    @Test
    fun `detects zip by mime type and content disposition`() {
        assertTrue(RemoteDownloadClassifier.isArchiveResponse("application/zip; charset=binary", null))
        assertTrue(
            RemoteDownloadClassifier.isArchiveResponse(
                "application/octet-stream",
                "attachment; filename=album.zip",
            ),
        )
        assertFalse(RemoteDownloadClassifier.isArchiveResponse("audio/mpeg", "inline; filename=song.mp3"))
    }

    @Test
    fun `detects standard zip bytes only`() {
        assertTrue(RemoteDownloadClassifier.hasZipSignature(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        assertFalse(RemoteDownloadClassifier.hasZipSignature("ID3!".encodeToByteArray()))
    }
}
