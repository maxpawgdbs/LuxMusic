package com.luxmusic.android.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PlaybackArtworkTest {
    @Test
    fun `reads small existing artwork`() {
        val file = File.createTempFile("luxmusic-artwork", ".jpg")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            file.writeBytes(bytes)
            assertArrayEquals(bytes, PlaybackArtwork.read(file.absolutePath))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `ignores missing empty and oversized artwork`() {
        val empty = File.createTempFile("luxmusic-empty-artwork", ".jpg")
        val oversized = File.createTempFile("luxmusic-large-artwork", ".jpg")
        try {
            oversized.writeBytes(ByteArray((PlaybackArtwork.MAX_BYTES + 1).toInt()))
            assertNull(PlaybackArtwork.read(null))
            assertNull(PlaybackArtwork.read(empty.absolutePath))
            assertNull(PlaybackArtwork.read(oversized.absolutePath))
            assertNull(PlaybackArtwork.read(File(empty.parentFile, "missing.jpg").absolutePath))
        } finally {
            empty.delete()
            oversized.delete()
        }
    }
}
