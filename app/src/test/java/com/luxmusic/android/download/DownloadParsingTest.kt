package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadParsingTest {
    @Test
    fun `detectService recognizes supported platforms`() {
        assertEquals(DownloadService.YOUTUBE, DownloadParsing.detectService("https://youtu.be/demo"))
        assertEquals(DownloadService.TIKTOK, DownloadParsing.detectService("https://vt.tiktok.com/demo"))
        assertEquals(DownloadService.SOUNDCLOUD, DownloadParsing.detectService("https://soundcloud.com/artist/track"))
        assertEquals(DownloadService.UNKNOWN, DownloadParsing.detectService("https://music.yandex.ru/album/1/track/2"))
        assertEquals(DownloadService.UNKNOWN, DownloadParsing.detectService("https://open.spotify.com/track/abc"))
        assertEquals(DownloadService.YOUTUBE, DownloadParsing.detectService("ytsearch1:artist song"))
        assertEquals(DownloadService.UNKNOWN, DownloadParsing.detectService("https://example.com/track"))
    }

    @Test
    fun `filterCookiesForService keeps only matching domains and preserves httpOnly cookies`() {
        val rawCookies = """
            # Netscape HTTP Cookie File
            #HttpOnly_.youtube.com	TRUE	/	TRUE	2147483647	SID	abc123
            .music.youtube.com	TRUE	/	FALSE	2147483647	PREF	wide
            .spotify.com	TRUE	/	FALSE	2147483647	sp_dc	secret
        """.trimIndent()

        val filtered = DownloadParsing.filterCookiesForService(DownloadService.YOUTUBE, rawCookies)

        assertNotNull(filtered)
        assertTrue(filtered!!.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(filtered.contains(".youtube.com\tTRUE\t/\tTRUE\t2147483647\tSID\tabc123"))
        assertTrue(filtered.contains(".music.youtube.com\tTRUE\t/\tFALSE\t2147483647\tPREF\twide"))
        assertFalse(filtered.contains("#HttpOnly_"))
        assertFalse(filtered.contains("spotify.com"))
    }

    @Test
    fun `filterCookiesForService returns null when there are no matching cookies`() {
        val rawCookies = """
            # Netscape HTTP Cookie File
            .spotify.com	TRUE	/	FALSE	2147483647	sp_dc	secret
        """.trimIndent()

        assertNull(DownloadParsing.filterCookiesForService(DownloadService.YOUTUBE, rawCookies))
    }

    @Test
    fun `cookieHeaderForHost returns only cookies for matching host`() {
        val cookiesText = """
            # Netscape HTTP Cookie File
            .youtube.com	TRUE	/	TRUE	2147483647	SID	abc123
            .music.youtube.com	TRUE	/	FALSE	2147483647	PREF	wide
            .spotify.com	TRUE	/	FALSE	2147483647	sp_dc	secret
        """.trimIndent()

        assertEquals(
            "SID=abc123; PREF=wide",
            DownloadParsing.cookieHeaderForHost("music.youtube.com", cookiesText),
        )
        assertEquals("SID=abc123", DownloadParsing.cookieHeaderForHost("m.youtube.com", cookiesText))
        assertNull(DownloadParsing.cookieHeaderForHost("soundcloud.com", cookiesText))
    }

    @Test
    fun `netscapeCookieLines converts cookie header to file lines`() {
        val lines = DownloadParsing.netscapeCookieLines(
            domain = "accounts.google.com",
            cookieHeader = "SID=abc123; HSID=secret; invalid; blank=",
        )

        assertEquals(
            listOf(
                ".accounts.google.com\tTRUE\t/\tTRUE\t2147483647\tSID\tabc123",
                ".accounts.google.com\tTRUE\t/\tTRUE\t2147483647\tHSID\tsecret",
            ),
            lines,
        )
    }

    @Test
    fun `cookiesFileFromHeaders builds netscape cookie file from multiple domains`() {
        val cookiesText = DownloadParsing.cookiesFileFromHeaders(
            mapOf(
                "accounts.google.com" to "SID=abc123; HSID=secret",
                "youtube.com" to "PREF=wide",
                "music.youtube.com" to null,
            ),
        )

        assertNotNull(cookiesText)
        assertTrue(cookiesText!!.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(cookiesText.contains(".accounts.google.com\tTRUE\t/\tTRUE\t2147483647\tSID\tabc123"))
        assertTrue(cookiesText.contains(".accounts.google.com\tTRUE\t/\tTRUE\t2147483647\tHSID\tsecret"))
        assertTrue(cookiesText.contains(".youtube.com\tTRUE\t/\tTRUE\t2147483647\tPREF\twide"))
    }

    @Test
    fun `htmlToSourceMetadata extracts title artist and query hint from html`() {
        val html = """
            <html>
            <head>
            <meta property="og:title" content="Song &amp; More" />
            <meta property="og:description" content="Artist | Album version" />
            </head>
            </html>
        """.trimIndent()

        val metadata = DownloadParsing.htmlToSourceMetadata(html)

        assertNotNull(metadata)
        assertEquals("Song & More", metadata?.title)
        assertEquals("Artist", metadata?.artist)
        assertEquals("Artist Song & More", metadata?.queryHint)
    }

    @Test
    fun `htmlToSourceMetadata handles bullet and em dash separators`() {
        val html = """
            <html>
            <head>
            <meta property="og:title" content="Track Title" />
            <meta property="og:description" content="Artist вЂў Album вЂ” Remaster" />
            </head>
            </html>
        """.trimIndent()

        val metadata = DownloadParsing.htmlToSourceMetadata(html)

        assertNotNull(metadata)
        assertEquals("Artist", metadata?.artist)
        assertEquals("Artist Track Title", metadata?.queryHint)
    }

    @Test
    fun `buildYoutubeFallbackQuery prefers query hint when present`() {
        val metadata = DownloadSourceMetadata(
            title = "Ignored",
            artist = "Ignored Artist",
            queryHint = "Exact match",
        )

        assertEquals("Exact match audio", DownloadParsing.buildYoutubeFallbackQuery(metadata))
    }

    @Test
    fun `buildYoutubeFallbackQuery falls back to artist and title`() {
        val metadata = DownloadSourceMetadata(
            title = "Track Name",
            artist = "Artist Name",
        )

        assertEquals("Artist Name Track Name audio", DownloadParsing.buildYoutubeFallbackQuery(metadata))
        assertNull(DownloadParsing.buildYoutubeFallbackQuery(DownloadSourceMetadata()))
    }

    @Test
    fun `appleMusicLookupKey extracts track id and country`() {
        val key = DownloadParsing.appleMusicLookupKey(
            "https://music.apple.com/us/album/song-name/1712345678?i=1712345680",
        )

        assertNotNull(key)
        assertEquals("us", key?.countryCode)
        assertEquals("1712345678", key?.resourceId)
        assertEquals("1712345680", key?.trackId)
        assertEquals("1712345680", key?.lookupId)
    }
}
