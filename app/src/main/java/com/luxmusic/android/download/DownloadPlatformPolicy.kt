package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService

internal enum class PlatformDownloadMode { DIRECT, CATALOG_MATCH, ACCOUNT, DEFERRED }

/** One source of truth for routing and the capability description shown before download. */
internal object DownloadPlatformPolicy {
    val directServices = setOf(
        DownloadService.YOUTUBE, DownloadService.TIKTOK, DownloadService.SOUNDCLOUD,
        DownloadService.BANDCAMP, DownloadService.INSTAGRAM, DownloadService.AUDIOMACK,
        DownloadService.JAMENDO, DownloadService.JIOSAAVN, DownloadService.RUTUBE,
        DownloadService.VK_VIDEO, DownloadService.YANDEX_VIDEO, DownloadService.DIRECT_FILE,
        DownloadService.UNKNOWN,
    )
    val catalogServices = setOf(
        DownloadService.SPOTIFY, DownloadService.VK_MUSIC, DownloadService.DEEZER,
        DownloadService.BOOMPLAY, DownloadService.ANGHAMI, DownloadService.AMAZON_MUSIC,
        DownloadService.PANDORA, DownloadService.ZVUK, DownloadService.KION_MUSIC,
    )

    fun mode(service: DownloadService): PlatformDownloadMode = when (service) {
        in directServices -> PlatformDownloadMode.DIRECT
        in catalogServices -> PlatformDownloadMode.CATALOG_MATCH
        DownloadService.YANDEX_MUSIC -> PlatformDownloadMode.ACCOUNT
        else -> PlatformDownloadMode.DEFERRED
    }

    fun hint(service: DownloadService): String = when (mode(service)) {
        PlatformDownloadMode.DIRECT -> when (service) {
            DownloadService.DIRECT_FILE -> "Прямое сохранение аудиофайла без запуска yt-dlp и перекодирования. HTTP-ссылки передаются без шифрования; предпочтительнее HTTPS."
            DownloadService.INSTAGRAM, DownloadService.AUDIOMACK, DownloadService.YANDEX_VIDEO ->
                "${service.title}: экспериментальная загрузка публичных записей. Работоспособность не подтверждена; сайт может ограничивать доступ."
            else -> "${service.title}: аудио из доступной публичной записи. Закрытые и защищённые записи не поддерживаются."
        }
        PlatformDownloadMode.CATALOG_MATCH ->
            "${service.title}: экспериментальный подбор по названию и исполнителю на YouTube, не оригинальный файл с площадки. Нужны публичные метаданные; версия записи может отличаться."
        PlatformDownloadMode.ACCOUNT -> "Яндекс Музыка: используется подключённый аккаунт и доступные ему записи."
        PlatformDownloadMode.DEFERRED -> if (service == DownloadService.BEATPORT)
            "Beatport пока отложен: публичный загрузчик возвращает превью, а не полный трек. Готовый купленный файл можно импортировать отдельно."
        else "${service.title} пока отложен: полный каталог требует подписки или покупки. Доступный вам готовый аудиофайл можно импортировать отдельно."
    }
}
