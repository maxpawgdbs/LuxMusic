package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.MetadataExtractor
import com.luxmusic.android.data.Track
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
) {
    private val mutableState = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var stableRefreshAttempted = false

    @Volatile
    private var nightlyRefreshAttempted = false

    private val youtubeDl by lazy { YoutubeDL.getInstance() }
    private val metadataExtractor by lazy { MetadataExtractor(context) }

    fun initialize() {
        if (initialized) return

        try {
            youtubeDl.init(context)
            initialized = true
            mutableState.value = DownloadState(
                isAvailable = true,
                statusMessage = "Вставьте ссылку. LuxMusic попробует сохранить аудио локально на устройство.",
            )
        } catch (error: Throwable) {
            mutableState.value = DownloadState(
                isAvailable = false,
                statusMessage = "Модуль загрузки не инициализировался. После обновления APK переустановите приложение и попробуйте снова.",
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    suspend fun download(url: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (!initialized) initialize()
        if (!initialized) {
            return@withContext Result.failure(
                IllegalStateException("Загрузчик не инициализирован. Проверьте модуль yt-dlp и переустановите APK."),
            )
        }

        val serviceLabel = detectService(url)
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Подготавливаем загрузчик для $serviceLabel.",
            errorMessage = null,
            isAvailable = true,
        )

        try {
            ensureStableExtractors(serviceLabel)
            var sourceInfo = fetchInfo(url)

            val imported = try {
                performDownloadAttempt(
                    requestUrl = url,
                    sourceUrl = url,
                    serviceLabel = serviceLabel,
                    expectedInfo = sourceInfo,
                )
            } catch (directError: Throwable) {
                var resolvedError = directError

                if (shouldRetryWithNightly(serviceLabel)) {
                    ensureNightlyExtractors(serviceLabel)
                    sourceInfo = fetchInfo(url)
                    runCatching {
                        performDownloadAttempt(
                            requestUrl = url,
                            sourceUrl = url,
                            serviceLabel = serviceLabel,
                            expectedInfo = sourceInfo,
                        )
                    }.getOrElse { nightlyError ->
                        resolvedError = nightlyError
                        emptyList()
                    }
                }

                val fallbackQuery = buildYoutubeFallbackQuery(sourceInfo)
                if (shouldTryYoutubeFallback(serviceLabel) && fallbackQuery != null) {
                    mutableState.value = mutableState.value.copy(
                        progress = 0.12f,
                        statusMessage = "Прямая загрузка из $serviceLabel не удалась. Ищем совпадение на YouTube.",
                    )

                    performDownloadAttempt(
                        requestUrl = "ytsearch1:$fallbackQuery",
                        sourceUrl = url,
                        serviceLabel = "$serviceLabel → YouTube",
                        expectedInfo = fetchInfo("ytsearch1:$fallbackQuery"),
                    )
                } else {
                    throw resolvedError
                }
            }

            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 1f,
                statusMessage = "Сохранено ${imported.size} трек(ов) из $serviceLabel в офлайн-библиотеку.",
                errorMessage = null,
                isAvailable = true,
            )

            Result.success(imported)
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку $serviceLabel.",
                errorMessage = error.message ?: serviceFailureHint(serviceLabel),
                isAvailable = initialized,
            )

            Result.failure(error)
        }
    }

    private fun ensureStableExtractors(serviceLabel: String) {
        if (stableRefreshAttempted) return
        stableRefreshAttempted = true

        mutableState.value = mutableState.value.copy(
            progress = 0.02f,
            statusMessage = "Обновляем extractor-модуль для $serviceLabel.",
        )

        runCatching {
            youtubeDl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        }
    }

    private fun ensureNightlyExtractors(serviceLabel: String) {
        if (nightlyRefreshAttempted) return
        nightlyRefreshAttempted = true

        mutableState.value = mutableState.value.copy(
            progress = 0.06f,
            statusMessage = "Повторяем запрос после обновления nightly extractor для $serviceLabel.",
        )

        runCatching {
            youtubeDl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
        }
    }

    private fun fetchInfo(url: String): VideoInfo? {
        return runCatching { youtubeDl.getInfo(buildInfoRequest(url)) }.getOrNull()
    }

    private suspend fun performDownloadAttempt(
        requestUrl: String,
        sourceUrl: String,
        serviceLabel: String,
        expectedInfo: VideoInfo?,
    ): List<Track> {
        val expectedDurationMs = expectedInfo?.duration?.takeIf { it > 0 }?.times(1_000L)
        val baseDir = File(context.cacheDir, "luxmusic-downloads").apply { mkdirs() }
        val jobId = "luxmusic-${System.currentTimeMillis()}"
        val jobDir = File(baseDir, jobId).apply { mkdirs() }

        return try {
            mutableState.value = mutableState.value.copy(
                progress = 0.1f,
                statusMessage = "Пробуем обработать ссылку: $serviceLabel.",
            )

            youtubeDl.execute(buildRequest(requestUrl, jobDir), jobId) { progress, _, line ->
                mutableState.value = mutableState.value.copy(
                    progress = progress.coerceIn(0f, 100f) / 100f,
                    statusMessage = line.takeIf { it.isNotBlank() }
                        ?: "Загружаем и сохраняем трек из $serviceLabel.",
                )
            }

            val files = jobDir.listFiles()?.toList().orEmpty()
            val audioFiles = files.filter { file ->
                file.isFile && file.extension.lowercase() in SUPPORTED_AUDIO_EXTENSIONS
            }

            if (audioFiles.isEmpty()) {
                throw IllegalStateException("После обработки ссылки не найден ни один подходящий аудиофайл.")
            }

            val selectedAudioFiles = selectAudioFiles(
                audioFiles = audioFiles,
                expectedDurationMs = expectedDurationMs,
                serviceLabel = serviceLabel,
            )

            val imported = libraryStore.importDownloadedFiles(
                audioFiles = selectedAudioFiles,
                sourceUrl = sourceUrl,
                companionResolver = { audio ->
                    files.filter { companion ->
                        companion != audio &&
                            companion.isFile &&
                            (
                                companion.nameWithoutExtension.equals(audio.nameWithoutExtension, ignoreCase = true) ||
                                    companion.nameWithoutExtension.startsWith(audio.nameWithoutExtension, ignoreCase = true)
                                )
                    }
                },
            )

            if (imported.isEmpty()) {
                throw IllegalStateException("Файлы скачались, но не были импортированы в библиотеку.")
            }

            imported
        } finally {
            cleanup(jobDir)
        }
    }

    private fun selectAudioFiles(
        audioFiles: List<File>,
        expectedDurationMs: Long?,
        serviceLabel: String,
    ): List<File> {
        val significantFiles = audioFiles
            .filter { it.length() >= MIN_AUDIO_BYTES }
            .ifEmpty { audioFiles }

        if (significantFiles.size == 1) {
            validateAudioFile(significantFiles.first(), expectedDurationMs, serviceLabel)
            return significantFiles
        }

        val bestFile = significantFiles.maxWithOrNull(
            compareBy<File> { candidateScore(it, expectedDurationMs) }
                .thenBy(File::length),
        ) ?: significantFiles.first()

        validateAudioFile(bestFile, expectedDurationMs, serviceLabel)
        return listOf(bestFile)
    }

    private fun candidateScore(file: File, expectedDurationMs: Long?): Long {
        val durationMs = metadataExtractor.probeDurationMs(file)
        return when {
            expectedDurationMs != null && durationMs > 0L -> -abs(expectedDurationMs - durationMs)
            durationMs > 0L -> durationMs
            else -> 0L
        }
    }

    private fun validateAudioFile(
        file: File,
        expectedDurationMs: Long?,
        serviceLabel: String,
    ) {
        val actualDurationMs = metadataExtractor.probeDurationMs(file)

        if (actualDurationMs <= 0L && file.length() < MIN_AUDIO_BYTES) {
            throw IllegalStateException("Сервис вернул пустой или неполный аудиофайл.")
        }

        if (
            expectedDurationMs != null &&
            expectedDurationMs >= LONG_TRACK_THRESHOLD_MS &&
            actualDurationMs in 1 until (expectedDurationMs * MIN_DURATION_RATIO).toLong()
        ) {
            throw IllegalStateException(partialTrackHint(serviceLabel, actualDurationMs, expectedDurationMs))
        }
    }

    private fun partialTrackHint(
        serviceLabel: String,
        actualDurationMs: Long,
        expectedDurationMs: Long,
    ): String {
        return when {
            "SoundCloud" in serviceLabel -> {
                "Похоже, сервис отдал превью вместо полного трека: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }

            "TikTok" in serviceLabel -> {
                "Ссылка TikTok вернула только аудио клипа: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }

            else -> {
                "Скачанный файл заметно короче ожидаемого: ${formatMinutes(actualDurationMs)} из ${formatMinutes(expectedDurationMs)}."
            }
        }
    }

    private fun buildRequest(url: String, jobDir: File): YoutubeDLRequest {
        return YoutubeDLRequest(url)
            .addOption(
                "-f",
                "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[acodec!=none]/best[acodec!=none]/bestaudio/best",
            )
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--restrict-filenames")
            .addOption("--no-part")
            .addOption("--abort-on-unavailable-fragments")
            .addOption("--retries", 10)
            .addOption("--fragment-retries", 10)
            .addOption("--extractor-retries", 3)
            .addOption("--socket-timeout", 30)
            .addOption("--write-thumbnail")
            .addOption("--write-info-json")
            .addOption("--write-auto-subs")
            .addOption("--sub-langs", "all")
            .addOption("-o", jobDir.absolutePath + "/%(title).140B.%(ext)s")
    }

    private fun buildInfoRequest(url: String): YoutubeDLRequest {
        return YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
    }

    private fun buildYoutubeFallbackQuery(info: VideoInfo?): String? {
        if (info == null) return null

        val title = info.title.normalizedOrNull()
            ?: info.fulltitle.normalizedOrNull()
            ?: return null
        val artist = info.uploader.normalizedOrNull()

        return if (artist != null && !title.contains(artist, ignoreCase = true)) {
            "$artist $title audio"
        } else {
            "$title audio"
        }
    }

    private fun shouldRetryWithNightly(serviceLabel: String): Boolean {
        return serviceLabel in DIRECT_RETRY_SERVICES && !nightlyRefreshAttempted
    }

    private fun shouldTryYoutubeFallback(serviceLabel: String): Boolean {
        return serviceLabel in FALLBACK_SEARCH_SERVICES
    }

    private fun cleanup(jobDir: File) {
        jobDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        runCatching { jobDir.delete() }
    }

    private fun detectService(url: String): String {
        val normalized = url.lowercase()
        return when {
            "music.yandex" in normalized || "yandex.ru" in normalized -> "Яндекс Музыка"
            "vk.com" in normalized || "vkvideo.ru" in normalized || "vk.ru" in normalized -> "VK / VK Музыка"
            "tiktok.com" in normalized || "vm.tiktok.com" in normalized -> "TikTok"
            "music.apple.com" in normalized || "itunes.apple.com" in normalized -> "Apple Music"
            "spotify.com" in normalized -> "Spotify"
            "soundcloud.com" in normalized -> "SoundCloud"
            "youtube.com" in normalized || "youtu.be" in normalized || "music.youtube.com" in normalized -> "YouTube"
            else -> "неизвестный сервис"
        }
    }

    private fun serviceFailureHint(serviceLabel: String): String {
        return when (serviceLabel) {
            "Spotify" -> "Spotify защищает потоковый контент DRM, поэтому прямая загрузка может не сработать."
            "Apple Music" -> "Для Apple Music прямой extractor нестабилен. LuxMusic попробует fallback по метаданным, но это доступно не для каждой ссылки."
            "Яндекс Музыка", "VK / VK Музыка" ->
                "Для этого сервиса загрузка зависит от актуальности extractor и доступности трека без дополнительных ограничений."
            else -> "Не удалось скачать музыку по ссылке."
        }
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun formatMinutes(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val MIN_AUDIO_BYTES = 96 * 1_024L
        const val LONG_TRACK_THRESHOLD_MS = 90_000L
        const val MIN_DURATION_RATIO = 0.6

        val DIRECT_RETRY_SERVICES = setOf(
            "YouTube",
            "SoundCloud",
            "TikTok",
            "Яндекс Музыка",
            "VK / VK Музыка",
        )

        val FALLBACK_SEARCH_SERVICES = setOf(
            "Spotify",
            "Apple Music",
            "Яндекс Музыка",
            "VK / VK Музыка",
        )

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
