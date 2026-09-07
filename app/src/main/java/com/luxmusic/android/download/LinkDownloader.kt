package com.luxmusic.android.download

import android.content.Context
import android.util.Log
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.ImportFileRules
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.MetadataExtractor
import com.luxmusic.android.data.Track
import com.luxmusic.android.download.yandex.YandexAuthState
import com.luxmusic.android.download.yandex.YandexDeviceCode
import com.luxmusic.android.download.yandex.YandexMusicDownloader
import com.luxmusic.android.download.yandex.YandexMusicUrlParser
import com.luxmusic.android.download.yandex.YandexPlaylistGroup
import com.luxmusic.android.download.yandex.YandexSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class DownloadCollectionResult(
    val tracks: List<Track>,
    val collectionLabel: String? = null,
    val sourceKind: YandexSourceKind? = null,
    val playlistGroups: List<YandexPlaylistGroup> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
) {
    private val mutableState = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()
    private val yandexDownloader = YandexMusicDownloader(context, libraryStore)
    val yandexAuthState: StateFlow<YandexAuthState> = yandexDownloader.authState

    private val backend = YtDlpMediaDownloadBackend(context)
    private val metadataResolver = CompositeDownloadMetadataResolver(
        backend = backend,
        httpClient = UrlConnectionMetadataHttpClient(),
    )
    private val executor = LinkDownloadExecutor(
        planner = DownloadPlanner(),
        metadataResolver = metadataResolver,
        backend = backend,
        importer = LibraryStoreImporter(libraryStore),
        audioInspector = MetadataAudioInspector(MetadataExtractor(context)),
        workspaceManager = CacheWorkspaceManager(context.cacheDir),
        tiktokFallback = TikTokFallbackBackend(UrlConnectionMetadataHttpClient(), backend),
    )
    private val operationGuard = DownloadOperationGuard()

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return

        try {
            backend.initialize()
            initialized = true
            mutableState.value = DownloadState(
                isAvailable = true,
                statusMessage = "Вставьте ссылку с YouTube, TikTok, SoundCloud или другой поддерживаемой площадки.",
            )
        } catch (error: Throwable) {
            Log.e(TAG, "yt-dlp initialization failed", error)
            mutableState.value = DownloadState(
                isAvailable = true,
                statusMessage = "Обычный загрузчик не инициализировался, но прямые аудиофайлы, ZIP и Яндекс Музыка доступны.",
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    suspend fun beginYandexAuthorization(): Result<YandexDeviceCode> =
        yandexDownloader.beginAuthorization()

    suspend fun completeYandexAuthorization(): Result<Unit> =
        yandexDownloader.completePendingAuthorization()

    fun hasPendingYandexAuthorization(): Boolean = yandexDownloader.hasPendingAuthorization()

    fun cancelYandexAuthorization() = yandexDownloader.cancelAuthorization()

    fun disconnectYandex() = yandexDownloader.disconnect()

    suspend fun download(url: String): Result<List<Track>> {
        return downloadCollection(url).map(DownloadCollectionResult::tracks)
    }

    suspend fun downloadCollection(url: String): Result<DownloadCollectionResult> = withContext(Dispatchers.IO) {
        if (!operationGuard.tryAcquire()) {
            return@withContext Result.failure(downloadAlreadyRunningError())
        }
        try {
            val normalizedUrl = DownloadParsing.normalizeUserInput(url)
            if (YandexMusicUrlParser.hasYandexMusicHost(normalizedUrl)) {
                return@withContext downloadYandex(normalizedUrl)
            }
            if (RemoteDownloadClassifier.isArchiveUrl(normalizedUrl)) {
                return@withContext downloadArchiveExclusive(normalizedUrl)
                    .map { tracks -> DownloadCollectionResult(tracks = tracks) }
            }
            val source = DownloadParsing.detectService(normalizedUrl)
            if (DownloadParsing.isDownloadableUrl(normalizedUrl) &&
                (source == DownloadService.DIRECT_FILE || source == DownloadService.UNKNOWN)) {
                downloadRemoteFileIfMedia(normalizedUrl)?.let { return@withContext it }
            }
            downloadGeneric(normalizedUrl).map { tracks -> DownloadCollectionResult(tracks = tracks) }
        } finally {
            mutableState.value = mutableState.value.copy(isRunning = false)
            operationGuard.release()
        }
    }

    private suspend fun downloadYandex(normalizedUrl: String): Result<DownloadCollectionResult> {
        return runCatching {
            YandexMusicUrlParser.parse(normalizedUrl)
            mutableState.value = DownloadState(
                isAvailable = true,
                isRunning = true,
                progress = 0f,
                statusMessage = "Подготавливаем загрузку из Яндекс Музыки...",
            )
            val result = yandexDownloader.download(normalizedUrl) { progress, message ->
                mutableState.value = DownloadState(
                    isAvailable = true,
                    isRunning = true,
                    progress = progress,
                    statusMessage = message,
                )
            }
            mutableState.value = DownloadState(
                isAvailable = true,
                progress = 1f,
                statusMessage = if (result.warnings.isEmpty()) {
                    "Из Яндекс Музыки сохранено ${result.tracks.size} трек(ов)."
                } else {
                    "Сохранено ${result.tracks.size} трек(ов), пропущено ${result.warnings.size}."
                },
            )
            DownloadCollectionResult(
                tracks = result.tracks,
                collectionLabel = result.collectionLabel,
                sourceKind = result.sourceKind,
                playlistGroups = result.playlistGroups,
                warnings = result.warnings,
            )
        }.onFailure { error ->
            Log.e(TAG, "Yandex Music download failed", error)
            if (error is CancellationException) throw error
            mutableState.value = DownloadState(
                isAvailable = true,
                statusMessage = "Не удалось скачать из Яндекс Музыки.",
                errorMessage = error.message
                    ?.replace(Regex("https?://\\S+"), "[ссылка скрыта]")
                    ?.take(400)
                    ?: "Ошибка Яндекс Музыки.",
            )
        }
    }

    private suspend fun downloadGeneric(url: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        val normalizedUrl = DownloadParsing.normalizeUserInput(url)
        if (!DownloadParsing.isDownloadableUrl(normalizedUrl)) {
            val error = IllegalArgumentException("Вставьте корректную ссылку на страницу с музыкой.")
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Ссылка не распознана.",
                errorMessage = error.message,
            )
            return@withContext Result.failure(error)
        }

        val sourceService = DownloadParsing.detectService(normalizedUrl)
        if (DownloadPlatformPolicy.mode(sourceService) == PlatformDownloadMode.DEFERRED) {
            val error = IllegalArgumentException(DownloadPlatformPolicy.hint(sourceService))
            mutableState.value = mutableState.value.copy(errorMessage = error.message, isRunning = false)
            return@withContext Result.failure(error)
        }
        if (!initialized) initialize()
        if (!initialized) {
            return@withContext Result.failure(
                IllegalStateException("Не удалось запустить загрузчик. Проверьте свободное место и повторите попытку."),
            )
        }

        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Подготавливаем загрузку для ${sourceService.title}.",
            errorMessage = null,
            isAvailable = true,
        )

        try {
            val result = executor.execute(
                sourceUrl = normalizedUrl,
                sessionProvider = { null },
            ) { progress, message ->
                mutableState.value = mutableState.value.copy(
                    isRunning = true,
                    progress = progress,
                    statusMessage = message,
                    errorMessage = null,
                    isAvailable = true,
                )
            }

            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 1f,
                statusMessage = successMessage(result),
                errorMessage = null,
                isAvailable = true,
            )

            Result.success(result.tracks)
        } catch (error: Throwable) {
            Log.e(TAG, "Generic link download failed for ${sourceService.name}", error)
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку ${sourceService.title}.",
                errorMessage = humanizeError(
                    service = sourceService,
                    error = error,
                ),
                isAvailable = true,
            )

            Result.failure(error)
        }
    }

    suspend fun downloadArchive(url: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (!operationGuard.tryAcquire()) {
            return@withContext Result.failure(downloadAlreadyRunningError())
        }
        try {
            downloadArchiveExclusive(url)
        } finally {
            mutableState.value = mutableState.value.copy(isRunning = false)
            operationGuard.release()
        }
    }

    private suspend fun downloadArchiveExclusive(url: String): Result<List<Track>> {
        val normalizedUrl = DownloadParsing.normalizeUserInput(url)
        if (!DownloadParsing.isDownloadableUrl(normalizedUrl)) {
            val error = IllegalArgumentException("Вставьте корректную прямую ссылку на ZIP-архив.")
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Ссылка на архив не распознана.",
                errorMessage = error.message,
            )
            return Result.failure(error)
        }
        if (!normalizedUrl.startsWith("https://", ignoreCase = true)) {
            val error = IllegalArgumentException("Для прямой загрузки ZIP используйте защищённую HTTPS-ссылку.")
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Нужна HTTPS-ссылка на архив.",
                errorMessage = error.message,
            )
            return Result.failure(error)
        }

        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Скачиваем ZIP-архив...",
            errorMessage = null,
        )

        val temporaryArchive = File(
            context.cacheDir,
            "luxmusic-archive-${UUID.randomUUID()}.zip",
        )
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = ARCHIVE_CONNECT_TIMEOUT_MS
                readTimeout = ARCHIVE_READ_TIMEOUT_MS
                setRequestProperty("User-Agent", ARCHIVE_USER_AGENT)
                setRequestProperty("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.1")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Сервер вернул HTTP $responseCode при скачивании архива.")
            }
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IllegalArgumentException("Ссылка на ZIP перенаправила на небезопасное соединение.")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > ImportFileRules.MAX_REMOTE_ZIP_BYTES) {
                throw IllegalArgumentException("ZIP-архив больше допустимого размера 1 ГБ.")
            }

            var downloadedBytes = 0L
            val progressLimiter = DownloadProgressLimiter()
            BufferedInputStream(connection.inputStream).use { input ->
                temporaryArchive.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloadedBytes += read
                        if (downloadedBytes > ImportFileRules.MAX_REMOTE_ZIP_BYTES) {
                            throw IllegalArgumentException("ZIP-архив больше допустимого размера 1 ГБ.")
                        }
                        output.write(buffer, 0, read)
                        val archiveProgress = if (contentLength > 0L) {
                            (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.9f)
                        } else {
                            0f
                        }
                        if (contentLength > 0L && progressLimiter.shouldPublish(archiveProgress)) {
                            mutableState.value = mutableState.value.copy(
                                isRunning = true,
                                progress = archiveProgress,
                                statusMessage = "Скачиваем ZIP-архив...",
                                errorMessage = null,
                            )
                        }
                    }
                }
            }

            val header = ByteArray(4)
            val headerBytes = temporaryArchive.inputStream().use { it.read(header) }
            if (headerBytes != header.size || !ImportFileRules.hasZipSignature(header)) {
                throw IllegalArgumentException("По ссылке получен не ZIP-архив.")
            }

            mutableState.value = mutableState.value.copy(
                isRunning = true,
                progress = 0.92f,
                statusMessage = "Импортируем музыку из архива...",
                errorMessage = null,
            )
            val tracks = libraryStore.importDownloadedArchive(
                archiveFile = temporaryArchive,
                sourceUrl = normalizedUrl,
            )
            if (tracks.isEmpty()) {
                throw IllegalArgumentException("В ZIP-архиве нет поддерживаемых аудиофайлов.")
            }

            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 1f,
                statusMessage = "Из ZIP-архива сохранено ${tracks.size} трек(ов).",
                errorMessage = null,
            )
            Result.success(tracks)
        } catch (error: Throwable) {
            Log.e(TAG, "Remote archive download/import failed", error)
            if (error is CancellationException) throw error
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось скачать или импортировать ZIP-архив.",
                errorMessage = DownloadFailureText.from(error, "Ошибка загрузки ZIP-архива."),
            )
            Result.failure(error)
        } finally {
            connection?.disconnect()
            temporaryArchive.delete()
        }
    }

    private fun downloadAlreadyRunningError(): IllegalStateException =
        IllegalStateException("Дождитесь завершения текущей загрузки.")

    private suspend fun downloadRemoteFileIfMedia(url: String): Result<DownloadCollectionResult>? {
        val workspaces = CacheWorkspaceManager(context.cacheDir)
        var workspace: File? = null
        try {
            val directory = workspaces.createWorkspace("remote")
            workspace = directory
            mutableState.value = mutableState.value.copy(isRunning = true, progress = 0.01f,
                statusMessage = "Получаем аудиофайл по прямой ссылке...", errorMessage = null)
            val remote = runInterruptible {
                RemoteFileTransfer().downloadIfMedia(url, directory) { progress ->
                    mutableState.value = mutableState.value.copy(progress = progress * 0.9f)
                }
            }
            if (remote == null) {
                if (DownloadParsing.detectService(url) == DownloadService.DIRECT_FILE)
                    throw IllegalArgumentException("По ссылке получена веб-страница, а не аудиофайл.")
                return null
            }
            currentCoroutineContext().ensureActive()
            val tracks = if (remote.kind == RemoteFileKind.ZIP) {
                libraryStore.importDownloadedArchive(remote.file, sourceUrl = url)
            } else {
                check(MetadataExtractor(context).probeDurationMs(remote.file) > 0) {
                    "Получен повреждённый или неподдерживаемый аудиофайл."
                }
                libraryStore.importDownloadedFiles(listOf(remote.file), sourceUrl = url) { emptyList() }
            }
            check(tracks.isNotEmpty()) { "В полученном файле нет поддерживаемой музыки." }
            mutableState.value = mutableState.value.copy(progress = 1f, isRunning = false,
                statusMessage = "По прямой ссылке сохранено ${tracks.size} трек(ов).", errorMessage = null)
            return Result.success(DownloadCollectionResult(tracks))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (DownloadParsing.detectService(url) == DownloadService.UNKNOWN &&
                error is RemoteFileHttpException && error.status in setOf(403, 406)) return null
            mutableState.value = mutableState.value.copy(isRunning = false, progress = 0f,
                errorMessage = DownloadFailureText.from(error, "Не удалось скачать аудиофайл."))
            return Result.failure(error)
        } finally { workspace?.let(workspaces::cleanup) }
    }

    private fun successMessage(result: DownloadExecutionResult): String {
        val tracksCount = result.tracks.size
        return when (result.finalAttempt.kind) {
            DownloadAttemptKind.DIRECT -> {
                "Сохранено $tracksCount трек(ов) из ${result.finalAttempt.sourceService.title} в офлайн-библиотеку."
            }

            DownloadAttemptKind.MATCHED_SEARCH -> {
                "Сохранено $tracksCount трек(ов). Ссылка ${result.finalAttempt.sourceService.title} была сопоставлена с офлайн-копией через ${result.finalAttempt.requestService.title}."
            }
        }
    }

    private fun humanizeError(service: DownloadService, error: Throwable): String =
        DownloadFailureText.forService(service, error)

    private class LibraryStoreImporter(
        private val libraryStore: LibraryStore,
    ) : DownloadedTrackImporter {
        override suspend fun importDownloadedFiles(
            audioFiles: List<File>,
            sourceUrl: String?,
            companionResolver: (File) -> List<File>,
        ): List<Track> {
            return libraryStore.importDownloadedFiles(
                audioFiles = audioFiles,
                sourceUrl = sourceUrl,
                companionResolver = companionResolver,
            )
        }
    }

    private class MetadataAudioInspector(
        private val extractor: MetadataExtractor,
    ) : DownloadAudioInspector {
        override fun probeDurationMs(file: File): Long = extractor.probeDurationMs(file)
    }

    private class CacheWorkspaceManager(
        private val cacheDir: File,
    ) : DownloadWorkspaceManager {
        override fun createWorkspace(prefix: String): File {
            return File(cacheDir, "luxmusic-$prefix-${UUID.randomUUID()}").apply {
                check(mkdirs()) { "Не удалось создать временную папку. Проверьте свободное место." }
            }
        }

        override fun cleanup(workspace: File) {
            runCatching { workspace.deleteRecursively() }
        }
    }

    private class UrlConnectionMetadataHttpClient : MetadataHttpClient {
        override fun getText(url: String, headers: Map<String, String>): String? {
            return runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                }

                try {
                    connection.inputStream.bufferedReader().use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8 * 1_024)
                        while (output.length < MAX_RESPONSE_CHARS) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Загрузка отменена.")
                            val read = reader.read(
                                buffer,
                                0,
                                minOf(buffer.size, MAX_RESPONSE_CHARS - output.length),
                            )
                            if (read < 0) break
                            output.append(buffer, 0, read)
                        }
                        output.toString()
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

        private companion object {
            const val TIMEOUT_MS = 8_000
            const val MAX_RESPONSE_CHARS = 1_000_000
        }
    }

    private companion object {
        const val ARCHIVE_CONNECT_TIMEOUT_MS = 20_000
        const val ARCHIVE_READ_TIMEOUT_MS = 60_000
        const val ARCHIVE_USER_AGENT = "LuxMusic/Android"
        const val TAG = "LuxMusicDownload"
    }
}
