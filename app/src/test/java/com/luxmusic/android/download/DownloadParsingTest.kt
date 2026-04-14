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
        assertEquals(DownloadService.YANDEX_MUSIC, DownloadParsing.detectService("https://music.yandex.ru/album/1/track/2"))
        assertEquals(DownloadService.SPOTIFY, DownloadParsing.detectService("https://open.spotify.com/track/abc"))
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
    fun `cookieHeaderFor returns only cookies for matching host`() {
        val cookiesText = """
            # Netscape HTTP Cookie File
            .youtube.com	TRUE	/	TRUE	2147483647	SID	abc123
            .music.youtube.com	TRUE	/	FALSE	2147483647	PREF	wide
            .spotify.com	TRUE	/	FALSE	2147483647	sp_dc	secret
        """.trimIndent()

        assertEquals(
            "SID=abc123; PREF=wide",
            DownloadParsing.cookieHeaderFor("music.youtube.com", cookiesText),
        )
        assertEquals("SID=abc123", DownloadParsing.cookieHeaderFor("m.youtube.com", cookiesText))
        assertNull(DownloadParsing.cookieHeaderFor("soundcloud.com", cookiesText))
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
        assertEquals("Song & More Artist | Album version", metadata?.queryHint)
    }

    @Test
    fun `htmlToSourceMetadata handles bullet and em dash separators`() {
        val html = """
            <html>
            <head>
            <meta property="og:title" content="Track Title" />
            <meta property="og:description" content="Artist • Album — Remaster" />
            </head>
            </html>
        """.trimIndent()

        val metadata = DownloadParsing.htmlToSourceMetadata(html)

        assertNotNull(metadata)
        assertEquals("Artist", metadata?.artist)
        assertEquals("Track Title Artist • Album — Remaster", metadata?.queryHint)
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
}
