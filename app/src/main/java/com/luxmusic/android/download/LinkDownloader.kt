package com.luxmusic.android.download

import android.content.Context
import android.os.Environment
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

class LinkDownloader(
    private val context: Context,
    private val libraryStore: LibraryStore,
) {
    private val mutableState = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    @Volatile
    private var initialized = false

    private var youtubeDlInstance: Any? = null
    private var youtubeDlClass: Class<*>? = null
    private var requestClass: Class<*>? = null

    fun initialize() {
        if (initialized) return

        try {
            val resolvedYoutubeDlClass = resolveClass(
                "com.yausername.youtubedl_android.YoutubeDL",
                "io.github.junkfood02.youtubedl_android.YoutubeDL",
            )
            val resolvedRequestClass = resolveClass(
                "com.yausername.youtubedl_android.YoutubeDLRequest",
                "io.github.junkfood02.youtubedl_android.YoutubeDLRequest",
            )

            youtubeDlClass = resolvedYoutubeDlClass
            requestClass = resolvedRequestClass

            youtubeDlInstance = resolvedYoutubeDlClass
                .getMethod("getInstance")
                .invoke(null)
                ?.also { instance ->
                    resolvedYoutubeDlClass.getMethod("init", Context::class.java).invoke(instance, context)
                }

            initialized = true
            mutableState.value = DownloadState()
        } catch (error: Exception) {
            mutableState.value = DownloadState(
                isAvailable = false,
                statusMessage = "Модуль загрузки пока недоступен на этом устройстве.",
                errorMessage = error.message,
            )
        }
    }

    suspend fun download(url: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        if (!initialized) initialize()
        if (!initialized) {
            return@withContext Result.failure(
                IllegalStateException("Загрузчик не инициализирован. Проверьте yt-dlp модуль."),
            )
        }

        val serviceLabel = detectService(url)

        val jobId = "luxmusic-${System.currentTimeMillis()}"
        val baseDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
            "luxmusic-downloads",
        ).apply { mkdirs() }
        val jobDir = File(baseDir, jobId).apply { mkdirs() }

        val request = buildRequest(url, jobDir)

        mutableState.value = mutableState.value.copy(
            isRunning = true,
            progress = 0f,
            statusMessage = "Пробуем обработать ссылку: $serviceLabel.",
            errorMessage = null,
        )

        runCatching {
            mutableState.value = mutableState.value.copy(progress = 0.2f)
            executeRequest(request)
            mutableState.value = mutableState.value.copy(progress = 0.8f)

            val files = jobDir.listFiles()?.toList().orEmpty()
            val audioFiles = files.filter { file ->
                file.isFile && file.extension.lowercase() in setOf(
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
            )

            imported
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isRunning = false,
                progress = 0f,
                statusMessage = "Не удалось обработать ссылку $serviceLabel.",
                errorMessage = error.message ?: serviceFailureHint(serviceLabel),
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

    private fun buildRequest(url: String, jobDir: File): Any {
        val klass = requestClass ?: error("YoutubeDLRequest class was not resolved.")
        val request = klass.getConstructor(String::class.java).newInstance(url)
        val addOptionOne = klass.methods.firstOrNull { it.name == "addOption" && it.parameterCount == 1 }
        val addOptionTwo = klass.methods.firstOrNull { it.name == "addOption" && it.parameterCount == 2 }

        addOptionTwo.invokeChecked(request, "-f", "bestaudio/best")
        addOptionOne.invokeChecked(request, "--no-playlist")
        addOptionOne.invokeChecked(request, "--restrict-filenames")
        addOptionOne.invokeChecked(request, "--write-thumbnail")
        addOptionOne.invokeChecked(request, "--write-info-json")
        addOptionOne.invokeChecked(request, "--write-auto-subs")
        addOptionTwo.invokeChecked(request, "--sub-langs", "all")
        addOptionTwo.invokeChecked(request, "-o", jobDir.absolutePath + "/%(title).140B.%(ext)s")

        return request
    }

    private fun executeRequest(request: Any) {
        val instance = youtubeDlInstance ?: error("YoutubeDL instance was not initialized.")
        val klass = youtubeDlClass ?: error("YoutubeDL class was not resolved.")

        val executeMethod = klass.methods.firstOrNull { method ->
            method.name == "execute" &&
                method.parameterCount == 1 &&
                method.parameterTypes.firstOrNull()?.isAssignableFrom(request::class.java) == true
        } ?: klass.methods.firstOrNull { method ->
            method.name == "execute" && method.parameterCount == 1
        } ?: error("No supported execute(request) method found on YoutubeDL.")

        executeMethod.invoke(instance, request)
    }

    private fun resolveClass(vararg candidates: String): Class<*> {
        return candidates.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        } ?: error("Class not found for ${candidates.joinToString()}")
    }

    private fun Method?.invokeChecked(target: Any, vararg args: Any) {
        check(this != null) { "Required YoutubeDLRequest.addOption overload is missing." }
        invoke(target, *args)
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
                "Для этого сервиса загрузка зависит от доступности публичного extractor/provider и может временно не сработать."
            else -> "Не удалось скачать музыку по ссылке."
        }
    }
}
