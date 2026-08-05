package com.luxmusic.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFileRulesTest {
    @Test
    fun `accepts supported audio and strips zip folders`() {
        assertEquals(
            "Track.FLAC",
            ImportFileRules.supportedZipAudioName("album/disc/Track.FLAC", false, 42L),
        )
    }

    @Test
    fun `normalizes windows separators without exposing traversal`() {
        assertEquals(
            "song.mp3",
            ImportFileRules.supportedZipAudioName("..\\folder\\song.mp3", false, 42L),
        )
    }

    @Test
    fun `rejects folders unsupported files and oversized entries`() {
        assertNull(ImportFileRules.supportedZipAudioName("music/", true, 0L))
        assertNull(ImportFileRules.supportedZipAudioName("cover.jpg", false, 42L))
        assertNull(
            ImportFileRules.supportedZipAudioName(
                "huge.mp3",
                false,
                ImportFileRules.MAX_ZIP_ENTRY_BYTES + 1L,
            ),
        )
    }
}
