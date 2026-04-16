package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.MetadataExtractor
import com.luxmusic.android.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
    private val accountStore: DownloadAccountStore,
) {
    private val mutableState = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

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
    )

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return

        try {
            backend.initialize()
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

        val sourceService = DownloadParsing.detectService(url)
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Подготавливаем загрузку для ${sourceService.title}.",
            errorMessage = null,
            isAvailable = true,
        )

        try {
            val result = executor.execute(
                sourceUrl = url,
                sessionProvider = { service -> accountStore.sessionFor(service)?.toDownloadSession() },
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
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку ${sourceService.title}.",
                errorMessage = humanizeError(
                    service = sourceService,
                    error = error,
                    hasSession = accountStore.sessionFor(sourceService) != null,
                ),
                isAvailable = true,
            )

            Result.failure(error)
        }
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
                    "YouTube вернул 429. Откройте вход для YouTube во вкладке загрузки и повторите попытку."
                }
            }

            service == DownloadService.SPOTIFY || service == DownloadService.APPLE_MUSIC -> {
                rawMessage.ifBlank {
                    "Для ${service.title} не получилось извлечь метаданные, поэтому LuxMusic не смог подобрать офлайн-копию."
                }
            }

            service == DownloadService.YANDEX_MUSIC || service == DownloadService.VK_MUSIC -> {
                rawMessage.ifBlank {
                    if (hasSession) {
                        "Прямая загрузка из ${service.title} не удалась, и fallback-поиск не нашёл совпадение."
                    } else {
                        "Прямая загрузка из ${service.title} без сессии не удалась. Подключите аккаунт или попробуйте другую ссылку."
                    }
                }
            }

            rawMessage.isNotBlank() -> rawMessage
            else -> serviceFailureHint(service)
        }
    }

    private fun serviceFailureHint(service: DownloadService): String {
        return when (service) {
            DownloadService.SPOTIFY ->
                "Spotify не отдаёт исходный аудиофайл через официальный Web API, поэтому LuxMusic работает только через метаданные и поиск совпадения."

            DownloadService.APPLE_MUSIC ->
                "Apple Music не отдаёт исходный аудиофайл через открытые API, поэтому LuxMusic работает через метаданные и поиск совпадения."

            DownloadService.YANDEX_MUSIC, DownloadService.VK_MUSIC ->
                "Для этого сервиса загрузка зависит от актуальности extractor и от подключенной пользовательской сессии."

            DownloadService.YOUTUBE ->
                "Не удалось скачать трек с YouTube. При 429 подключите аккаунт и повторите попытку."

            else -> "Не удалось скачать музыку по ссылке."
        }
    }

    private fun DownloadAccountStore.StoredAccountSession.toDownloadSession(): DownloadSession {
        return DownloadSession(
            cookiesText = cookiesText,
            userAgent = userAgent,
        )
    }

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
            return File(cacheDir, "luxmusic-$prefix-${System.currentTimeMillis()}").apply { mkdirs() }
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

                connection.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        }

        private companion object {
            const val TIMEOUT_MS = 30_000
        }
    }
}
