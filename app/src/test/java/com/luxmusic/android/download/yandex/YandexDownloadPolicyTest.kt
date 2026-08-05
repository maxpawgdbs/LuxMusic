package com.luxmusic.android.download.yandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexDownloadPolicyTest {
    @Test
    fun selectsHighestFullSupportedOptionAndPrefersMp3OnTie() {
        val selected = YandexDownloadPolicy.selectBestOption(
            listOf(
                option("mp3", 320, preview = true),
                option("aac", 320),
                option("mp3", 320),
                option("flac", 1_000),
                option("mp3", 192),
            ),
        )

        assertEquals("mp3", selected?.codec)
        assertEquals(320, selected?.bitrateKbps)
    }

    @Test
    fun returnsNullWhenOnlyPreviewOrUnsupportedOptionsExist() {
        assertNull(
            YandexDownloadPolicy.selectBestOption(
                listOf(option("mp3", 320, preview = true), option("flac", 1_000)),
            ),
        )
    }

    @Test
    fun createsSignedDirectLinkFromYandexXml() {
        val xml = """
            <download-info>
              <host>storage.yandex.net</host>
              <path>/music/file.mp3</path>
              <ts>123456</ts>
              <s>secret</s>
            </download-info>
        """.trimIndent()

        assertEquals(
            "https://storage.yandex.net/get-mp3/24f762905f33a5abba4ab8ebd9c4a11d/123456/music/file.mp3",
            YandexDownloadPolicy.buildDirectLink(xml),
        )
    }

    @Test
    fun acceptsCurrentOpaqueTimestampTokens() {
        val xml = """
            <download-info>
              <host>storage.yandex.net</host>
              <path>/track/file.mp3</path>
              <ts>67d2a0f1_AZ-9.~</ts>
              <s>secret</s>
            </download-info>
        """.trimIndent()

        assertTrue(
            YandexDownloadPolicy.buildDirectLink(xml)
                .contains("/67d2a0f1_AZ-9.~/track/file.mp3"),
        )
    }

    @Test
    fun rejectsTimestampUrlInjection() {
        val maliciousValues = listOf("abc/next", "abc?admin=true", "abc#fragment", "abc%2Fnext", "")
        maliciousValues.forEach { timestamp ->
            val xml = """
                <download-info>
                  <host>storage.yandex.net</host>
                  <path>/track/file.mp3</path>
                  <ts>$timestamp</ts>
                  <s>secret</s>
                </download-info>
            """.trimIndent()
            assertFails { YandexDownloadPolicy.buildDirectLink(xml) }
        }
    }

    @Test
    fun rejectsUntrustedDownloadHostAndTraversal() {
        val maliciousHost = "<host>evil.example</host><path>/a.mp3</path><ts>1</ts><s>x</s>"
        val traversal = "<host>storage.yandex.net</host><path>/../a.mp3</path><ts>1</ts><s>x</s>"

        assertFails { YandexDownloadPolicy.buildDirectLink(maliciousHost) }
        assertFails { YandexDownloadPolicy.buildDirectLink(traversal) }
    }

    @Test
    fun normalizesTrustedCoverAndRejectsLookalikes() {
        assertEquals(
            "https://avatars.yandex.net/get-music/600x600",
            YandexDownloadPolicy.coverUrl("avatars.yandex.net/get-music/%%"),
        )
        assertNull(YandexDownloadPolicy.coverUrl("https://yandex.net.evil.example/image/%%"))
        assertNull(YandexDownloadPolicy.coverUrl("http://avatars.yandex.net/image/%%"))
    }

    @Test
    fun validatesOnlyHttpsYandexFileUrls() {
        assertTrue(YandexDownloadPolicy.isTrustedYandexUrl("https://storage.yandex.net/info"))
        assertFalse(YandexDownloadPolicy.isTrustedYandexUrl("http://storage.yandex.net/info"))
        assertFalse(YandexDownloadPolicy.isTrustedYandexUrl("https://storage.yandex.net.evil.test/info"))
        assertFalse(YandexDownloadPolicy.isTrustedYandexUrl("https://user@storage.yandex.net/info"))
    }

    @Test
    fun acceptsCurrentYandexDeviceVerificationShortLink() {
        assertTrue(YandexOAuthPolicy.isTrustedVerificationUrl("https://ya.ru/device"))
        assertTrue(YandexOAuthPolicy.isTrustedVerificationUrl("https://oauth.yandex.ru/device"))
        assertFalse(YandexOAuthPolicy.isTrustedVerificationUrl("http://ya.ru/device"))
        assertFalse(YandexOAuthPolicy.isTrustedVerificationUrl("https://ya.ru.evil.test/device"))
    }

    @Test
    fun recognizesCurrentPendingAuthorizationResponse() {
        assertTrue(YandexOAuthPolicy.isAuthorizationPending("authorization_pending", "pending"))
        assertTrue(
            YandexOAuthPolicy.isAuthorizationPending(
                null,
                "User has not yet authorized your application",
            ),
        )
        assertFalse(YandexOAuthPolicy.isAuthorizationPending("access_denied", "User denied access"))
    }

    @Test
    fun pendingDeviceAuthorizationRequiresAtLeastOneSecondRemaining() {
        assertTrue(YandexOAuthPolicy.isPendingUsable(expiresAtEpochMs = 11_000L, nowEpochMs = 10_000L))
        assertFalse(YandexOAuthPolicy.isPendingUsable(expiresAtEpochMs = 10_999L, nowEpochMs = 10_000L))
    }

    private fun option(codec: String, bitrate: Int, preview: Boolean = false) = YandexDownloadOption(
        codec = codec,
        bitrateKbps = bitrate,
        preview = preview,
        downloadInfoUrl = "https://storage.yandex.net/info",
    )

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
