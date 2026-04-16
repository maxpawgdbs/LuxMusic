package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

internal class LinkDownloadExecutor(
    private val planner: DownloadPlanner,
    private val metadataResolver: CompositeDownloadMetadataResolver,
    private val backend: MediaDownloadBackend,
    private val importer: DownloadedTrackImporter,
    private val audioInspector: DownloadAudioInspector,
    private val workspaceManager: DownloadWorkspaceManager,
) {
    @Volatile
    private var stableRefreshAttempted = false

    @Volatile
    private var nightlyRefreshAttempted = false

    suspend fun execute(
        sourceUrl: String,
        sessionProvider: (DownloadService) -> DownloadSession?,
        onStatus: (progress: Float, message: String) -> Unit,
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        val sourceService = DownloadParsing.detectService(sourceUrl)
        val sourceSession = sessionProvider(sourceService)

        refreshStableExtractorsIfNeeded(sourceService, onStatus)

        val sourceMetadata = metadataResolver.resolve(sourceUrl, sourceService, sourceSession)
        val plan = planner.createPlan(
            sourceUrl = sourceUrl,
            sourceService = sourceService,
            metadata = sourceMetadata,
            hasSession = sourceSession != null,
        )

        if (plan.attempts.isEmpty()) {
            throw IllegalStateException(noDownloadPlanHint(sourceService))
        }

        var lastError: Throwable? = null
        for (attempt in plan.attempts) {
            val attemptSession = sessionProvider(attempt.requestService)
            val directAttempt = runCatching {
                performAttempt(
                    plan = plan,
                    attempt = attempt,
                    session = attemptSession,
                    onStatus = onStatus,
                )
            }

            if (directAttempt.isSuccess) {
                return@withContext DownloadExecutionResult(
                    tracks = directAttempt.getOrThrow(),
                    finalAttempt = attempt,
                )
            }

            lastError = directAttempt.exceptionOrNull()

            if (attempt.allowsNightlyRetry && refreshNightlyExtractorsIfNeeded(attempt.requestService, onStatus)) {
                val nightlyAttempt = runCatching {
                    performAttempt(
                        plan = plan,
                        attempt = attempt,
                        session = attemptSession,
                        onStatus = onStatus,
                    )
                }
                if (nightlyAttempt.isSuccess) {
                    return@withContext DownloadExecutionResult(
                        tracks = nightlyAttempt.getOrThrow(),
                        finalAttempt = attempt,
                    )
                }
                lastError = nightlyAttempt.exceptionOrNull()
            }
        }

        throw lastError ?: IllegalStateException(noDownloadPlanHint(sourceService))
    }

    private suspend fun performAttempt(
        plan: DownloadPlan,
        attempt: DownloadAttempt,
        session: DownloadSession?,
        onStatus: (progress: Float, message: String) -> Unit,
    ): List<Track> {
        val workspace = workspaceManager.createWorkspace("download")

        return try {
            onStatus(
                if (attempt.kind == DownloadAttemptKind.DIRECT) 0.1f else 0.16f,
                attempt.label,
            )

            backend.download(
                requestUrl = attempt.requestUrl,
                service = attempt.requestService,
                session = session,
                outputDir = workspace,
            ) { progress, line ->
                onStatus(
                    progress.coerceIn(0f, 100f) / 100f,
                    line?.takeIf { it.isNotBlank() }
                        ?: defaultProgressMessage(attempt),
                )
            }

            val files = workspace.listFiles()?.toList().orEmpty()
            val audioFiles = files.filter { file ->
                file.isFile && file.extension.lowercase() in SUPPORTED_AUDIO_EXTENSIONS
            }

            if (audioFiles.isEmpty()) {
                throw IllegalStateException("После обработки ссылки не найден ни один подходящий аудиофайл.")
            }

            val selectedAudioFiles = selectAudioFiles(
                audioFiles = audioFiles,
                expectedDurationMs = attempt.expectedMetadata?.durationMs,
                service = attempt.requestService,
            )

            val imported = importer.importDownloadedFiles(
                audioFiles = selectedAudioFiles,
                sourceUrl = plan.sourceUrl,
                companionResolver = { audio ->
                    files.filter { candidate ->
                        candidate != audio &&
                            candidate.isFile &&
                            (
                                candidate.nameWithoutExtension.equals(audio.nameWithoutExtension, ignoreCase = true) ||
                                    candidate.nameWithoutExtension.startsWith(audio.nameWithoutExtension, ignoreCase = true)
                                )
                    }
                },
            )

            if (imported.isEmpty()) {
                throw IllegalStateException("Файлы скачались, но не были импортированы в библиотеку.")
            }

            imported
        } finally {
            workspaceManager.cleanup(workspace)
        }
    }

    private fun refreshStableExtractorsIfNeeded(
        service: DownloadService,
        onStatus: (progress: Float, message: String) -> Unit,
    ) {
        if (stableRefreshAttempted) return
        stableRefreshAttempted = true
        onStatus(0.02f, "Обновляем extractor-модуль для ${service.title}.")
        runCatching { backend.update(ExtractorChannel.STABLE) }
    }

    private fun refreshNightlyExtractorsIfNeeded(
        service: DownloadService,
        onStatus: (progress: Float, message: String) -> Unit,
    ): Boolean {
        if (nightlyRefreshAttempted) return false
        nightlyRefreshAttempted = true
        onStatus(0.06f, "Повторяем запрос после обновления nightly extractor для ${service.title}.")
        runCatching { backend.update(ExtractorChannel.NIGHTLY) }
        return true
    }

    private fun selectAudioFiles(
        audioFiles: List<File>,
        expectedDurationMs: Long?,
        service: DownloadService,
    ): List<File> {
        val significantFiles = audioFiles
            .filter { it.length() >= MIN_AUDIO_BYTES }
            .ifEmpty { audioFiles }

        if (significantFiles.size == 1) {
            validateAudioFile(significantFiles.first(), expectedDurationMs, service)
            return significantFiles
        }

        val bestFile = significantFiles.maxWithOrNull(
            compareBy<File> { candidateScore(it, expectedDurationMs) }
                .thenByDescending(File::length),
        ) ?: significantFiles.first()

        validateAudioFile(bestFile, expectedDurationMs, service)
        return listOf(bestFile)
    }

    private fun candidateScore(file: File, expectedDurationMs: Long?): Long {
        val durationMs = audioInspector.probeDurationMs(file)
        return when {
            expectedDurationMs != null && durationMs > 0L -> -abs(expectedDurationMs - durationMs)
            durationMs > 0L -> durationMs
            else -> 0L
        }
    }

    private fun validateAudioFile(
        file: File,
        expectedDurationMs: Long?,
        service: DownloadService,
    ) {
        val actualDurationMs = audioInspector.probeDurationMs(file)

        if (actualDurationMs <= 0L && file.length() < MIN_AUDIO_BYTES) {
            throw IllegalStateException("Сервис вернул пустой или неполный аудиофайл.")
        }

        if (
            expectedDurationMs != null &&
            expectedDurationMs >= LONG_TRACK_THRESHOLD_MS &&
            actualDurationMs in 1 until (expectedDurationMs * MIN_DURATION_RATIO).toLong()
        ) {
            throw IllegalStateException(partialTrackHint(service, actualDurationMs, expectedDurationMs))
        }
    }

    private fun partialTrackHint(
        service: DownloadService,
        actualDurationMs: Long,
        expectedDurationMs: Long,
    ): String {
        return when (service) {
            DownloadService.SOUNDCLOUD -> {
                "Похоже, сервис отдал превью вместо полного трека: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }

            DownloadService.TIKTOK -> {
                "Ссылка TikTok вернула только аудио клипа: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }

            else -> {
                "Скачанный файл заметно короче ожидаемого: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }
        }
    }

    private fun formatMinutes(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun defaultProgressMessage(attempt: DownloadAttempt): String {
        return when (attempt.kind) {
            DownloadAttemptKind.DIRECT -> "Скачиваем и сохраняем трек из ${attempt.requestService.title}."
            DownloadAttemptKind.MATCHED_SEARCH -> "Скачиваем совпадение через ${attempt.requestService.title}."
        }
    }

    private fun noDownloadPlanHint(service: DownloadService): String {
        return when {
            planner.requiresMetadataBeforeDownload(service) -> {
                "Не удалось извлечь метаданные из ссылки ${service.title}. Без названия и исполнителя LuxMusic не сможет подобрать офлайн-копию."
            }

            else -> "Не удалось подготовить план скачивания для ${service.title}."
        }
    }

    private companion object {
        const val MIN_AUDIO_BYTES = 96 * 1_024L
        const val LONG_TRACK_THRESHOLD_MS = 90_000L
        const val MIN_DURATION_RATIO = 0.6

        val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3",
            "m4a",
            "aac",
            "flac",
            "wav",
            "ogg",
            "opus",
            "webm",
            "mp4",
        )
    }
}
