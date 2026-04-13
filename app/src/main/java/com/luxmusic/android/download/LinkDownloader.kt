package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.Track
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
) {
    private val mutableState = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var extractorRefreshAttempted = false

    private val youtubeDl by lazy { YoutubeDL.getInstance() }

    fun initialize() {
        if (initialized) return

        try {
            youtubeDl.init(context)
            initialized = true
            mutableState.value = DownloadState(
                isAvailable = true,
                statusMessage = "Вставьте ссылку. LuxMusic попытается сохранить аудио локально на устройство.",
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
        val jobId = "luxmusic-${System.currentTimeMillis()}"
        val baseDir = File(context.cacheDir, "luxmusic-downloads").apply { mkdirs() }
        val jobDir = File(baseDir, jobId).apply { mkdirs() }
        val request = buildRequest(url, jobDir)

        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Подготавливаем загрузчик для $serviceLabel.",
            errorMessage = null,
        )

        runCatching {
            if (!extractorRefreshAttempted) {
                extractorRefreshAttempted = true
                runCatching { youtubeDl.updateYoutubeDL(context) }
            }

            mutableState.value = mutableState.value.copy(
                progress = 0.05f,
                statusMessage = "Пробуем обработать ссылку: $serviceLabel.",
            )

            youtubeDl.execute(request, jobId) { progress, _, line ->
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

            val imported = libraryStore.importDownloadedFiles(
                audioFiles = audioFiles,
                sourceUrl = url,
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

            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 1f,
                statusMessage = "Сохранено ${imported.size} трек(ов) из $serviceLabel в офлайн-библиотеку.",
                errorMessage = null,
                isAvailable = true,
            )

            imported
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку $serviceLabel.",
                errorMessage = error.message ?: serviceFailureHint(serviceLabel),
                isAvailable = initialized,
            )
        }.also {
            cleanup(jobDir)
        }
    }

    private fun cleanup(jobDir: File) {
        jobDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        runCatching { jobDir.delete() }
    }

    private fun buildRequest(url: String, jobDir: File): YoutubeDLRequest {
        return YoutubeDLRequest(url)
            .addOption("-f", "bestaudio/best")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--restrict-filenames")
            .addOption("--write-thumbnail")
            .addOption("--write-info-json")
            .addOption("--write-auto-subs")
            .addOption("--sub-langs", "all")
            .addOption("-o", jobDir.absolutePath + "/%(title).140B.%(ext)s")
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
            "Spotify", "Apple Music", "Яндекс Музыка", "VK / VK Музыка" ->
                "Для этого сервиса загрузка зависит от доступности публичного extractor и может временно не сработать."
            else -> "Не удалось скачать музыку по ссылке."
        }
    }

    private companion object {
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
