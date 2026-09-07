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

        if (
            sourceService in DownloadPlatformPolicy.directServices &&
            (DownloadParsing.isDownloadableUrl(sourceUrl) || sourceUrl.startsWith("ytsearch", ignoreCase = true))
        ) {
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
                allowsNightlyRetry = sourceService != DownloadService.DIRECT_FILE,
            )
        }

        val fallbackQuery = DownloadParsing.buildYoutubeFallbackQuery(metadata)
        if (fallbackQuery != null && sourceService in DownloadPlatformPolicy.catalogServices) {
            attempts += DownloadAttempt(
                requestUrl = "ytsearch1:$fallbackQuery",
                requestService = DownloadService.YOUTUBE,
                sourceService = sourceService,
                kind = DownloadAttemptKind.MATCHED_SEARCH,
                expectedMetadata = metadata,
                label = "Подбираем совпадение в YouTube по метаданным ${sourceService.title}.",
                allowsNightlyRetry = true,
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
        return service in DownloadPlatformPolicy.catalogServices
    }

}
