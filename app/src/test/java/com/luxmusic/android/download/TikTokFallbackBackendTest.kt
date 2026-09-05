package com.luxmusic.android.download

import org.junit.Assert.*
import org.junit.Test

class TikTokFallbackBackendTest {
    @Test fun `video uses clip stream and retains artist and title`() {
        val media = TikTokFallbackBackend.parseResponse("""{"code":0,"data":{
            "play":"/video/clip.mp4","music":"https://example.test/full-song.mp3",
            "title":"Клип","author":{"nickname":"Автор"}}}""")
        assertEquals("https://tikwm.com/video/clip.mp4", media.url)
        assertEquals("Клип", media.title)
        assertEquals("Автор", media.artist)
    }

    @Test fun `photo post uses its audio track`() {
        val media = TikTokFallbackBackend.parseResponse("""{"code":0,"data":{
            "images":["photo.jpg"],"play":"https://example.test/slideshow.mp4",
            "music_info":{"play":"https://example.test/audio.mp3","title":"Песня","author":"Артист"}}}""")
        assertEquals("https://example.test/audio.mp3", media.url)
        assertEquals("Песня", media.title)
    }

    @Test fun `unavailable posts malformed json and missing audio fail clearly`() {
        listOf("{", """{"code":-1}""", """{"code":0,"data":{}}""").forEach {
            assertNotNull(runCatching { TikTokFallbackBackend.parseResponse(it) }.exceptionOrNull())
        }
    }

    @Test fun `non https media and credentials in url are rejected`() {
        listOf("file:///music.mp3", "http://example.test/a.mp3", "https://user:pass@example.test/a.mp3").forEach {
            assertNotNull(runCatching {
                TikTokFallbackBackend.parseResponse("""{"code":0,"data":{"play":"$it"}}""")
            }.exceptionOrNull())
        }
    }

    @Test fun `youtube rate limits and restricted videos do not suggest cookies`() {
        listOf("HTTP Error 429", "Sign in to confirm your age", "use --cookies", "not a bot").forEach {
            val message = DownloadFailureText.forService(com.luxmusic.android.data.DownloadService.YOUTUBE, Exception(it))
            assertFalse(message.contains("cookies", true))
            assertFalse(message.contains("войти", true))
        }
    }
}
