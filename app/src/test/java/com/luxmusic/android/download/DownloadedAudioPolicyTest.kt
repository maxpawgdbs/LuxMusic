package com.luxmusic.android.download

import org.junit.Assert.*
import org.junit.Test

class DownloadedAudioPolicyTest {
    @Test fun `rejects explicitly labelled previews`() {
        assertNotNull(runCatching { DownloadedAudioPolicy.validateMetadata("""{"format_id":"hls_mp3_preview"}""", 30_000) }.exceptionOrNull())
    }
    @Test fun `rejects a short fragment of a long recording`() {
        assertNotNull(runCatching { DownloadedAudioPolicy.validateMetadata("""{"duration":210}""", 30_000) }.exceptionOrNull())
    }
    @Test fun `accepts complete and legitimately short recordings`() {
        DownloadedAudioPolicy.validateMetadata("""{"duration":210}""", 210_000)
        DownloadedAudioPolicy.validateMetadata("""{"duration":10.8}""", 10_840)
    }
}
