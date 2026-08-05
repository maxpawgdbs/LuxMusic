package com.luxmusic.android.download

import java.io.IOException

internal object DownloadFailureText {
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
