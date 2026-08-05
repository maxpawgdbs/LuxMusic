package com.luxmusic.android.download.yandex

import java.net.URI
import java.security.MessageDigest

object YandexDownloadPolicy {
    fun selectBestOption(options: List<YandexDownloadOption>): YandexDownloadOption? {
        return options.asSequence()
            .filterNot(YandexDownloadOption::preview)
            .filter { it.codec.lowercase() in setOf("mp3", "aac") }
            .maxWithOrNull(
                compareBy<YandexDownloadOption> { it.bitrateKbps }
                    .thenBy { it.codec.equals("mp3", ignoreCase = true) },
            )
    }

    fun buildDirectLink(xml: String): String {
        val host = xml.tagValue("host").lowercase()
        val path = xml.tagValue("path")
        val timestamp = xml.tagValue("ts")
        val secret = xml.tagValue("s")
        require(isTrustedYandexHost(host)) { "Некорректный адрес аудиофайла." }
        require(path.startsWith('/') && !path.contains("..")) { "Некорректный путь аудиофайла." }
        require(timestamp.all(Char::isDigit)) { "Некорректная отметка времени аудиофайла." }
        val signature = md5(SIGN_SALT + path.drop(1) + secret)
        return "https://$host/get-mp3/$signature/$timestamp$path"
    }

    fun coverUrl(rawCoverUri: String?): String? {
        val raw = rawCoverUri?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val url = (if (raw.startsWith("http")) raw else "https://$raw").replace("%%", "600x600")
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return url.takeIf {
            uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && isTrustedYandexHost(host)
        }
    }

    fun isTrustedYandexUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && isTrustedYandexHost(host)
    }

    private fun isTrustedYandexHost(host: String): Boolean {
        return host == "yandex.net" || host.endsWith(".yandex.net") ||
            host == "yandex.ru" || host.endsWith(".yandex.ru")
    }

    private fun String.tagValue(tag: String): String {
        val match = Regex("<$tag>([^<]+)</$tag>").find(this)
        return match?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Некорректные данные для загрузки трека.")
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private const val SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA"
}
