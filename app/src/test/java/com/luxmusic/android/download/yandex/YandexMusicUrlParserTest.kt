package com.luxmusic.android.download.yandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMusicUrlParserTest {
    @Test
    fun parsesTrackAlbumAndArtistUrls() {
        assertEquals(
            YandexMusicSource(YandexSourceKind.TRACK, 456L, 123L),
            YandexMusicUrlParser.parse("https://music.yandex.ru/album/123/track/456"),
        )
        assertEquals(
            YandexMusicSource(YandexSourceKind.TRACK, 456L),
            YandexMusicUrlParser.parse("https://music.yandex.com/track/456?utm_source=test#player"),
        )
        assertEquals(
            YandexMusicSource(YandexSourceKind.ALBUM, 123L),
            YandexMusicUrlParser.parse("https://music.yandex.kz/album/123/"),
        )
        assertEquals(
            YandexMusicSource(YandexSourceKind.ARTIST, 789L),
            YandexMusicUrlParser.parse("https://music.yandex.com.tr/artist/789"),
        )
    }

    @Test
    fun acceptsAllSupportedRegionalHosts() {
        val hosts = listOf(
            "music.yandex.ru",
            "music.yandex.com",
            "music.yandex.by",
            "music.yandex.kz",
            "music.yandex.uz",
            "music.yandex.az",
            "music.yandex.com.tr",
        )
        hosts.forEach { host ->
            assertEquals(42L, YandexMusicUrlParser.parse("https://$host/track/42").entityId)
        }
    }

    @Test
    fun rejectsLookalikeHostsAndUnsafeAuthority() {
        val invalid = listOf(
            "https://music.yandex.ru.evil.example/track/1",
            "https://evil.example/music.yandex.ru/track/1",
            "https://user:pass@music.yandex.ru/track/1",
            "https://music.yandex.ru:443/track/1",
            "ftp://music.yandex.ru/track/1",
        )
        invalid.forEach { value ->
            assertNull(value, YandexMusicUrlParser.parseOrNull(value))
        }
    }

    @Test
    fun rejectsUnsupportedOrMalformedPaths() {
        val invalid = listOf(
            "https://music.yandex.ru/playlist/1",
            "https://music.yandex.ru/track/0",
            "https://music.yandex.ru/track/-1",
            "https://music.yandex.ru/track/9223372036854775808",
            "https://music.yandex.ru/album/1/../track/2",
            "https://music.yandex.ru/album/1/track/2/extra",
            "https://music.yandex.ru/track/1 extra",
        )
        invalid.forEach { value ->
            assertNull(value, YandexMusicUrlParser.parseOrNull(value))
        }
    }

    @Test
    fun detectsYandexHostEvenWhenPathIsNotSupported() {
        assertTrue(YandexMusicUrlParser.hasYandexMusicHost("https://music.yandex.ru/playlist/12"))
    }
}
