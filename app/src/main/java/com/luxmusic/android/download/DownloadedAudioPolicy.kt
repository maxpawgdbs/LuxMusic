package com.luxmusic.android.download

import org.json.JSONObject

internal object DownloadedAudioPolicy {
    fun validateMetadata(json: String, actualDurationMs: Long) {
        val metadata = runCatching { JSONObject(json) }.getOrNull() ?: return
        val format = metadata.optString("format_id")
        check(!format.contains("preview", ignoreCase = true) && !metadata.optBoolean("is_preview")) {
            "Площадка вернула только превью. Полный трек недоступен без дополнительного доступа."
        }
        val expectedSeconds = metadata.optDouble("duration", 0.0)
        if (expectedSeconds.isFinite() && expectedSeconds >= 90) {
            check(actualDurationMs >= expectedSeconds * 1_000 * 0.6) {
                "Получен короткий фрагмент вместо полного трека. Файл не сохранён."
            }
        }
    }
}
