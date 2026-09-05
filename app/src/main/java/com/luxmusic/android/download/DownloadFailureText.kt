package com.luxmusic.android.download

import java.io.IOException
import com.luxmusic.android.data.DownloadService

internal object DownloadFailureText {
    fun forService(service: DownloadService, error: Throwable): String {
        val raw = from(error, "Не удалось скачать музыку. Повторите попытку позже.")
        if (service == DownloadService.YOUTUBE) {
            return when {
                raw.contains("429") || raw.contains("too many requests", true) ->
                    "YouTube временно ограничил запросы. Подождите и повторите попытку позже."
                listOf("sign in", "sign-in", "confirm your age", "private video", "not a bot", "cookies")
                    .any { raw.contains(it, true) } ->
                    "YouTube ограничил доступ к этому видео. Попробуйте другую публичную ссылку или повторите позже."
                raw.contains("javascript", true) || raw.contains("challenge", true) ->
                    "Не удалось обработать видео YouTube. Проверьте обновления приложения и повторите позже."
                else -> raw
            }
        }
        return raw
    }

    fun from(error: Throwable, fallback: String): String {
        val specific = generateSequence(error) { it.cause }
            .take(8)
            .mapNotNull { cause -> cause.message?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()

        val message = when {
            error is OutOfMemoryError ->
                "Недостаточно памяти для обработки файла. Попробуйте файл или архив меньшего размера."
            specific != null -> specific
            error is IOException ->
                "Ошибка сети или чтения файла. Проверьте соединение и доступ к файлу."
            else -> fallback
        }
        return message
            .replace(Regex("https?://\\S+"), "[ссылка скрыта]")
            .replace(Regex("(?i)(oauth|token|code)=?[^\\s&]+"), "$1=[скрыто]")
            .take(400)
    }
}
