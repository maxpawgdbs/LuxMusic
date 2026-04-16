package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService

internal class DownloadPlanner {
    fun createPlan(
        sourceUrl: String,
        sourceService: DownloadService,
        metadata: DownloadSourceMetadata?,
        hasSession: Boolean,
    ): DownloadPlan {
        val attempts = mutableListOf<DownloadAttempt>()

        if (sourceService in DIRECT_DOWNLOAD_SERVICES) {
            attempts += DownloadAttempt(
                requestUrl = sourceUrl,
                requestService = sourceService,
                sourceService = sourceService,
                kind = DownloadAttemptKind.DIRECT,
                expectedMetadata = metadata,
                label = if (sourceService.requiresAccount && !hasSession) {
                    "Пробуем прямую загрузку из ${sourceService.title} без сессии."
                } else {
                    "Пробуем прямую загрузку из ${sourceService.title}."
                },
                allowsNightlyRetry = sourceService in NIGHTLY_RETRY_SERVICES,
            )
        }

        val fallbackQuery = DownloadParsing.buildYoutubeFallbackQuery(metadata)
        if (fallbackQuery != null && sourceService in YOUTUBE_FALLBACK_SERVICES) {
            attempts += DownloadAttempt(
                requestUrl = "ytsearch1:$fallbackQuery",
                requestService = DownloadService.YOUTUBE,
                sourceService = sourceService,
                kind = DownloadAttemptKind.MATCHED_SEARCH,
                expectedMetadata = metadata,
                label = "Подбираем совпадение в YouTube по метаданным ${sourceService.title}.",
                allowsNightlyRetry = DownloadService.YOUTUBE in NIGHTLY_RETRY_SERVICES,
            )
        }

        return DownloadPlan(
            sourceUrl = sourceUrl,
            sourceService = sourceService,
            metadata = metadata,
            attempts = attempts,
        )
    }

    fun requiresMetadataBeforeDownload(service: DownloadService): Boolean {
        return service in METADATA_ONLY_SERVICES
    }

    private companion object {
        val DIRECT_DOWNLOAD_SERVICES = setOf(
            DownloadService.YOUTUBE,
            DownloadService.TIKTOK,
            DownloadService.SOUNDCLOUD,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
            DownloadService.UNKNOWN,
        )

        val YOUTUBE_FALLBACK_SERVICES = setOf(
            DownloadService.APPLE_MUSIC,
            DownloadService.SPOTIFY,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
            DownloadService.UNKNOWN,
        )

        val METADATA_ONLY_SERVICES = setOf(
            DownloadService.APPLE_MUSIC,
            DownloadService.SPOTIFY,
        )

        val NIGHTLY_RETRY_SERVICES = setOf(
            DownloadService.YOUTUBE,
            DownloadService.TIKTOK,
            DownloadService.SOUNDCLOUD,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
        )
    }
}
