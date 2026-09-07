package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.*
import org.junit.Test

class DownloadPlatformPolicyTest {
    @Test fun `recognizes new platforms and their aliases`() {
        val cases = mapOf(
            "artist.bandcamp.com/track/song" to DownloadService.BANDCAMP,
            "instagram.com/reel/abc" to DownloadService.INSTAGRAM,
            "audiomack.com/artist/song/title" to DownloadService.AUDIOMACK,
            "jamendo.com/track/123/title" to DownloadService.JAMENDO,
            "jiosaavn.com/song/title/token" to DownloadService.JIOSAAVN,
            "saavn.com/s/song/a/b/c/token" to DownloadService.JIOSAAVN,
            "rutube.ru/video/123" to DownloadService.RUTUBE,
            "vkvideo.ru/video-123_4" to DownloadService.VK_VIDEO,
            "vk.ru/clip123_4" to DownloadService.VK_VIDEO,
            "vk.com/video_ext.php?oid=1&id=2" to DownloadService.VK_VIDEO,
            "vk.com/feed?z=video-123_4%2Fabc" to DownloadService.VK_VIDEO,
            "vk.com/audio123_4" to DownloadService.VK_MUSIC,
            "yandex.ru/video/preview/123" to DownloadService.YANDEX_VIDEO,
            "frontend.vh.yandex.ru/player/id" to DownloadService.YANDEX_VIDEO,
            "dzen.ru/video/watch/123" to DownloadService.YANDEX_VIDEO,
            "deezer.com/en/track/123" to DownloadService.DEEZER,
            "deezer.page.link/abc" to DownloadService.DEEZER,
            "boomplay.com/songs/123" to DownloadService.BOOMPLAY,
            "play.anghami.com/song/123" to DownloadService.ANGHAMI,
            "music.amazon.co.uk/albums/123" to DownloadService.AMAZON_MUSIC,
            "pandora.com/artist/song/123" to DownloadService.PANDORA,
            "zvuk.com/track/123" to DownloadService.ZVUK,
            "music.kion.ru/track/123" to DownloadService.KION_MUSIC,
            "music.mts.ru/track/123" to DownloadService.KION_MUSIC,
            "tidal.com/browse/track/123" to DownloadService.TIDAL,
            "open.qobuz.com/track/123" to DownloadService.QOBUZ,
            "beatport.com/track/title/123" to DownloadService.BEATPORT,
            "spotify.link/abc" to DownloadService.SPOTIFY,
            "cdn.example.com/song.MP3?token=abc" to DownloadService.DIRECT_FILE,
        )
        cases.forEach { (url, service) -> assertEquals(url, service, DownloadParsing.detectService("https://$url")) }
    }

    @Test fun `domain lookalikes never acquire a platform identity`() {
        listOf("deezer.com.invalid", "notbandcamp.com", "vkvideo.ru.attacker.test", "music.amazon.com.invalid",
            "example.org/?next=instagram.com", "example.org/spotify.com").forEach {
            assertEquals(it, DownloadService.UNKNOWN, DownloadParsing.detectService("https://$it"))
        }
    }

    @Test fun `subscription and preview only sites are deferred without a search`() {
        listOf(DownloadService.TIDAL, DownloadService.QOBUZ, DownloadService.APPLE_MUSIC, DownloadService.BEATPORT).forEach {
            assertEquals(PlatformDownloadMode.DEFERRED, DownloadPlatformPolicy.mode(it))
            assertFalse(DownloadPlanner().requiresMetadataBeforeDownload(it))
            assertTrue(DownloadPlanner().createPlan("https://example.com/track", it,
                DownloadSourceMetadata("Title", "Artist"), false).attempts.isEmpty())
        }
    }

    @Test fun `all catalog matches are disclosed as non original YouTube results`() {
        DownloadPlatformPolicy.catalogServices.forEach {
            assertTrue(DownloadPlatformPolicy.hint(it).contains("не оригинальный файл"))
            val plan = DownloadPlanner().createPlan("https://example.com/track", it,
                DownloadSourceMetadata("Title", "Artist"), false)
            assertEquals(DownloadAttemptKind.MATCHED_SEARCH, plan.attempts.single().kind)
        }
    }

    @Test fun `known direct platforms avoid metadata lookups`() {
        DownloadPlatformPolicy.directServices.forEach { assertFalse(DownloadPlanner().requiresMetadataBeforeDownload(it)) }
    }
}
