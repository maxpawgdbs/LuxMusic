package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadService
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
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
    private val accountStore: DownloadAccountStore,
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

        val service = detectService(url)
        val session = accountStore.sessionFor(service)
        if (service.requiresAccount && session == null) {
            val error = IllegalStateException(accountRequiredMessage(service))
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Для ${service.title} требуется аккаунт.",
                errorMessage = error.message,
                isAvailable = initialized,
            )
            return@withContext Result.failure(error)
        }

        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Подготавливаем загрузчик для ${service.title}.",
            errorMessage = null,
            isAvailable = true,
        )

        try {
            ensureStableExtractors(service)
            var sourceMetadata = resolveSourceMetadata(url, service, session)

            val imported = try {
                performDownloadAttempt(
                    requestUrl = url,
                    sourceUrl = url,
                    service = service,
                    expectedMetadata = sourceMetadata,
                    session = session,
                )
            } catch (directError: Throwable) {
                var resolvedError = directError

                if (shouldRetryWithNightly(service)) {
                    ensureNightlyExtractors(service)
                    sourceMetadata = resolveSourceMetadata(url, service, session)
                    runCatching {
                        performDownloadAttempt(
                            requestUrl = url,
                            sourceUrl = url,
                            service = service,
                            expectedMetadata = sourceMetadata,
                            session = session,
                        )
                    }.getOrElse { nightlyError ->
                        resolvedError = nightlyError
                        emptyList()
                    }
                }

                val fallbackQuery = buildYoutubeFallbackQuery(sourceMetadata)
                if (shouldTryYoutubeFallback(service) && fallbackQuery != null) {
                    val youtubeSession = accountStore.sessionFor(DownloadService.YOUTUBE)
                    mutableState.value = mutableState.value.copy(
                        progress = 0.12f,
                        statusMessage = "Прямая загрузка из ${service.title} не удалась. Ищем совпадение на YouTube.",
                    )

                    performDownloadAttempt(
                        requestUrl = "ytsearch1:$fallbackQuery",
                        sourceUrl = url,
                        service = DownloadService.YOUTUBE,
                        expectedMetadata = resolveSourceMetadata(
                            url = "ytsearch1:$fallbackQuery",
                            service = DownloadService.YOUTUBE,
                            session = youtubeSession,
                        ),
                        session = youtubeSession,
                    )
                } else {
                    throw resolvedError
                }
            }

            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 1f,
                statusMessage = "Сохранено ${imported.size} трек(ов) из ${service.title} в офлайн-библиотеку.",
                errorMessage = null,
                isAvailable = true,
            )

            Result.success(imported)
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку ${service.title}.",
                errorMessage = humanizeError(service, error, session != null),
                isAvailable = initialized,
            )

            Result.failure(error)
        }
    }

    private fun ensureStableExtractors(service: DownloadService) {
        if (stableRefreshAttempted) return
        stableRefreshAttempted = true

        mutableState.value = mutableState.value.copy(
            progress = 0.02f,
            statusMessage = "Обновляем extractor-модуль для ${service.title}.",
        )

        runCatching {
            youtubeDl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        }
    }

    private fun ensureNightlyExtractors(service: DownloadService) {
        if (nightlyRefreshAttempted) return
        nightlyRefreshAttempted = true

        mutableState.value = mutableState.value.copy(
            progress = 0.06f,
            statusMessage = "Повторяем запрос после обновления nightly extractor для ${service.title}.",
        )

        runCatching {
            youtubeDl.updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
        }
    }

    private fun fetchInfo(
        url: String,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): VideoInfo? {
        val workspace = createWorkspace("info")
        return try {
            runCatching { youtubeDl.getInfo(buildInfoRequest(url, workspace, service, session)) }.getOrNull()
        } finally {
            cleanup(workspace)
        }
    }

    private suspend fun performDownloadAttempt(
        requestUrl: String,
        sourceUrl: String,
        service: DownloadService,
        expectedMetadata: SourceMetadata?,
        session: DownloadAccountStore.StoredAccountSession?,
    ): List<Track> {
        val jobDir = createWorkspace("download")
        val jobId = "luxmusic-${System.currentTimeMillis()}"

        return try {
            mutableState.value = mutableState.value.copy(
                progress = 0.1f,
                statusMessage = "Пробуем обработать ссылку: ${service.title}.",
            )

            youtubeDl.execute(buildDownloadRequest(requestUrl, jobDir, service, session), jobId) { progress, _, line ->
                mutableState.value = mutableState.value.copy(
                    progress = progress.coerceIn(0f, 100f) / 100f,
                    statusMessage = line.takeIf { it.isNotBlank() }
                        ?: "Загружаем и сохраняем трек из ${service.title}.",
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
                expectedDurationMs = expectedMetadata?.durationMs,
                service = service,
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
        service: DownloadService,
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

    private fun buildDownloadRequest(
        url: String,
        jobDir: File,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
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
            .addOption("--retries", serviceRequestRetries(service))
            .addOption("--fragment-retries", serviceFragmentRetries(service))
            .addOption("--extractor-retries", serviceExtractorRetries(service))
            .addOption("--socket-timeout", serviceSocketTimeoutSeconds(service))
            .addOption("--sleep-requests", serviceSleepRequestsSeconds(service, session))
            .addOption("--write-thumbnail")
            .addOption("--write-info-json")
            .addOption("--write-auto-subs")
            .addOption("--sub-langs", "all")
            .addOption("-o", jobDir.absolutePath + "/%(title).140B.%(ext)s")

        return applySessionOptions(request, jobDir, service, session)
    }

    private fun buildInfoRequest(
        url: String,
        jobDir: File,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--socket-timeout", serviceSocketTimeoutSeconds(service))
            .addOption("--sleep-requests", serviceSleepRequestsSeconds(service, session))

        return applySessionOptions(request, jobDir, service, session)
    }

    private fun buildYoutubeFallbackQuery(metadata: SourceMetadata?): String? {
        return DownloadParsing.buildYoutubeFallbackQuery(metadata?.toDownloadSourceMetadata())
    }

    private fun applySessionOptions(
        request: YoutubeDLRequest,
        jobDir: File,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): YoutubeDLRequest {
        if (session == null) return request

        val cookieFile = File(jobDir, "${service.name.lowercase()}-cookies.txt").apply {
            writeText(session.cookiesText)
        }
        request.addOption("--cookies", cookieFile.absolutePath)
        session.userAgent?.takeIf { it.isNotBlank() }?.let { userAgent ->
            request.addOption("--user-agent", userAgent)
        }
        return request
    }

    private fun resolveSourceMetadata(
        url: String,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): SourceMetadata? {
        val info = fetchInfo(url, service, session)
        if (info != null) {
            return info.toSourceMetadata()
        }

        if (url.startsWith("ytsearch", ignoreCase = true)) {
            return SourceMetadata(queryHint = url.substringAfter(':').trim())
        }

        return fetchWebPageMetadata(url, service, session)
    }

    private fun fetchWebPageMetadata(
        url: String,
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): SourceMetadata? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = serviceSocketTimeoutSeconds(service) * 1_000
                readTimeout = serviceSocketTimeoutSeconds(service) * 1_000
                setRequestProperty("User-Agent", session?.userAgent ?: FALLBACK_USER_AGENT)
                cookieHeaderFor(url, session)?.let { setRequestProperty("Cookie", it) }
            }

            connection.inputStream.bufferedReader().use { reader ->
                htmlToSourceMetadata(reader.readText())
            }
        }.getOrNull()
    }

    private fun htmlToSourceMetadata(html: String): SourceMetadata? {
        return DownloadParsing.htmlToSourceMetadata(html)?.toSourceMetadata()
    }

    private fun cookieHeaderFor(
        url: String,
        session: DownloadAccountStore.StoredAccountSession?,
    ): String? {
        if (session == null) return null
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return null
        return DownloadParsing.cookieHeaderFor(host, session.cookiesText)
    }

    private fun shouldRetryWithNightly(service: DownloadService): Boolean {
        return service in DIRECT_RETRY_SERVICES && !nightlyRefreshAttempted
    }

    private fun shouldTryYoutubeFallback(service: DownloadService): Boolean {
        return service in FALLBACK_SEARCH_SERVICES
    }

    private fun cleanup(jobDir: File) {
        jobDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        runCatching { jobDir.delete() }
    }

    private fun detectService(url: String): DownloadService {
        return DownloadParsing.detectService(url)
    }

    private fun humanizeError(
        service: DownloadService,
        error: Throwable,
        hasSession: Boolean,
    ): String {
        val rawMessage = error.message.orEmpty()

        return when {
            service == DownloadService.YOUTUBE &&
                (rawMessage.contains("429") || rawMessage.contains("Too Many Requests", ignoreCase = true)) -> {
                if (hasSession) {
                    "YouTube вернул 429 даже с подключенной сессией. Подождите немного и повторите попытку позже."
                } else {
                    "YouTube вернул 429. На вкладке загрузки откройте вход для YouTube прямо в приложении и повторите попытку."
                }
            }

            service == DownloadService.YANDEX_MUSIC &&
                rawMessage.contains("timed out", ignoreCase = true) -> {
                if (hasSession) {
                    "Яндекс Музыка отвечает слишком долго даже с подключенной сессией. LuxMusic увеличил таймаут, но сервис все равно не успел ответить."
                } else {
                    "Яндекс Музыка не ответила вовремя. Подключите аккаунт Яндекса прямо в приложении и повторите попытку."
                }
            }

            rawMessage.isNotBlank() -> rawMessage
            else -> serviceFailureHint(service)
        }
    }

    private fun serviceFailureHint(service: DownloadService): String {
        return when (service) {
            DownloadService.SPOTIFY ->
                "Spotify защищает потоковый контент DRM, поэтому прямая загрузка может не сработать даже с аккаунтом."
            DownloadService.APPLE_MUSIC ->
                "Apple Music использует закрытые потоки и нестабильный extractor. LuxMusic попробует fallback по метаданным, если это возможно."
            DownloadService.YANDEX_MUSIC, DownloadService.VK_MUSIC ->
                "Для этого сервиса загрузка зависит от актуальности extractor и от подключенной пользовательской сессии."
            DownloadService.YOUTUBE ->
                "Не удалось скачать трек с YouTube. При 429 подключите аккаунт и повторите попытку."
            else -> "Не удалось скачать музыку по ссылке."
        }
    }

    private fun serviceRequestRetries(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 15
        else -> 10
    }

    private fun serviceFragmentRetries(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 15
        else -> 10
    }

    private fun serviceExtractorRetries(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 6
        else -> 3
    }

    private fun serviceSocketTimeoutSeconds(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 60
        else -> 30
    }

    private fun serviceSleepRequestsSeconds(
        service: DownloadService,
        session: DownloadAccountStore.StoredAccountSession?,
    ): Int = when (service) {
        DownloadService.YOUTUBE -> if (session == null) 3 else 1
        else -> 1
    }

    private fun accountRequiredMessage(service: DownloadService): String {
        return "Для ${service.title} сначала подключите аккаунт на вкладке загрузки: войдите прямо в приложении и завершите авторизацию."
    }

    private fun VideoInfo.toSourceMetadata(): SourceMetadata = SourceMetadata(
        title = title.normalizedOrNull() ?: fulltitle.normalizedOrNull(),
        artist = uploader.normalizedOrNull(),
        durationMs = duration.takeIf { it > 0 }?.times(1_000L),
        queryHint = listOfNotNull(uploader.normalizedOrNull(), title.normalizedOrNull())
            .joinToString(" ")
            .takeIf { it.isNotBlank() },
    )

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun formatMinutes(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun createWorkspace(prefix: String): File {
        return File(context.cacheDir, "luxmusic-$prefix-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    private data class SourceMetadata(
        val title: String? = null,
        val artist: String? = null,
        val durationMs: Long? = null,
        val queryHint: String? = null,
    )

    private fun SourceMetadata.toDownloadSourceMetadata(): DownloadSourceMetadata = DownloadSourceMetadata(
        title = title,
        artist = artist,
        durationMs = durationMs,
        queryHint = queryHint,
    )

    private fun DownloadSourceMetadata.toSourceMetadata(): SourceMetadata = SourceMetadata(
        title = title,
        artist = artist,
        durationMs = durationMs,
        queryHint = queryHint,
    )

    private companion object {
        const val MIN_AUDIO_BYTES = 96 * 1_024L
        const val LONG_TRACK_THRESHOLD_MS = 90_000L
        const val MIN_DURATION_RATIO = 0.6
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; LuxMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"

        val DIRECT_RETRY_SERVICES = setOf(
            DownloadService.YOUTUBE,
            DownloadService.SOUNDCLOUD,
            DownloadService.TIKTOK,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
        )

        val FALLBACK_SEARCH_SERVICES = setOf(
            DownloadService.SPOTIFY,
            DownloadService.APPLE_MUSIC,
            DownloadService.YANDEX_MUSIC,
            DownloadService.VK_MUSIC,
            DownloadService.UNKNOWN,
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
