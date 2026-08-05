package com.luxmusic.android.download.yandex

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.luxmusic.android.data.DownloadedTrackImport
import com.luxmusic.android.data.LibraryStore
import com.luxmusic.android.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

data class YandexPlaylistGroup(
    val name: String,
    val trackIds: List<String>,
)

data class YandexDownloadResult(
    val tracks: List<Track>,
    val collectionLabel: String,
    val sourceKind: YandexSourceKind,
    val playlistGroups: List<YandexPlaylistGroup>,
    val warnings: List<String>,
)

private data class PendingYandexAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresAtEpochMs: Long,
    val intervalSeconds: Long,
) {
    fun toDeviceCode(nowEpochMs: Long = System.currentTimeMillis()): YandexDeviceCode = YandexDeviceCode(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUrl = verificationUrl,
        expiresInSeconds = ((expiresAtEpochMs - nowEpochMs) / 1_000L).coerceAtLeast(1L),
        intervalSeconds = intervalSeconds,
    )

    fun toAuthState(
        statusMessage: String =
            "Код сохранён. Подтвердите вход в браузере — ожидание продолжится в фоне.",
        errorMessage: String? = null,
    ): YandexAuthState = YandexAuthState(
        isAuthorizing = true,
        verificationUrl = verificationUrl,
        userCode = userCode,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
    )

    companion object {
        fun from(code: YandexDeviceCode, nowEpochMs: Long = System.currentTimeMillis()) =
            PendingYandexAuthorization(
                deviceCode = code.deviceCode,
                userCode = code.userCode,
                verificationUrl = code.verificationUrl,
                expiresAtEpochMs = nowEpochMs + code.expiresInSeconds * 1_000L,
                intervalSeconds = code.intervalSeconds,
            )
    }
}

class YandexMusicDownloader(
    context: Context,
    private val libraryStore: LibraryStore,
    private val api: YandexMusicHttpClient = YandexMusicHttpClient(),
) {
    private val appContext = context.applicationContext
    private val tokenStore = YandexTokenStore(appContext)
    private val authorizationRequestMutex = Mutex()
    private val authorizationPollingMutex = Mutex()
    private val mutableAuthState = MutableStateFlow(tokenStore.authState())
    val authState: StateFlow<YandexAuthState> = mutableAuthState.asStateFlow()

    suspend fun beginAuthorization(): Result<YandexDeviceCode> = withContext(Dispatchers.IO) {
        authorizationRequestMutex.withLock {
            runCatching {
                tokenStore.load()?.let {
                    throw IllegalStateException("Аккаунт Яндекс Музыки уже подключён.")
                }
                tokenStore.loadPending()?.let { pending ->
                    mutableAuthState.value = pending.toAuthState()
                    return@runCatching pending.toDeviceCode()
                }
                mutableAuthState.value = YandexAuthState(
                    isAuthorizing = true,
                    statusMessage = "Получаем код подтверждения...",
                )
                val code = api.requestDeviceCode(
                    deviceId = tokenStore.deviceId,
                    deviceName = "LuxMusic — ${Build.MODEL.take(60)}",
                )
                val pending = PendingYandexAuthorization.from(code)
                tokenStore.savePending(pending)
                mutableAuthState.value = pending.toAuthState()
                code
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableAuthState.value = YandexAuthState(
                    statusMessage = "Не удалось подключить Яндекс Музыку.",
                    errorMessage = error.userMessage("Ошибка авторизации Яндекс Музыки."),
                )
            }
        }
    }

    suspend fun completePendingAuthorization(): Result<Unit> = withContext(Dispatchers.IO) {
        authorizationPollingMutex.withLock {
            runCatching {
                tokenStore.load()?.let { stored ->
                    mutableAuthState.value = stored.toAuthState()
                    return@runCatching
                }
                val pending = tokenStore.loadPending()
                    ?: error("Код подтверждения истёк. Начните подключение снова.")
                mutableAuthState.value = pending.toAuthState()

                var token: YandexOAuthToken? = null
                while (YandexOAuthPolicy.isPendingUsable(pending.expiresAtEpochMs) && token == null) {
                    delay(pending.intervalSeconds * 1_000L)
                    token = try {
                        api.pollDeviceToken(pending.deviceCode)
                    } catch (_: IOException) {
                        mutableAuthState.value = pending.toAuthState(
                            statusMessage = "Нет сети. Продолжаем ждать подтверждение...",
                        )
                        null
                    }
                }
                requireNotNull(token) { "Время подтверждения истекло. Запустите вход ещё раз." }
                val accountName = api.validateAccount(token.accessToken)
                tokenStore.save(token, accountName)
                tokenStore.clearPending()
                mutableAuthState.value = YandexAuthState(
                    isConnected = true,
                    accountName = accountName,
                    statusMessage = "Аккаунт подключён. Доступны треки, альбомы и дискографии артистов.",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val pending = tokenStore.loadPending()
                if (pending == null || !YandexOAuthPolicy.isPendingUsable(pending.expiresAtEpochMs)) {
                    tokenStore.clearPending()
                }
                mutableAuthState.value = pending
                    ?.takeIf { YandexOAuthPolicy.isPendingUsable(it.expiresAtEpochMs) }
                    ?.toAuthState(
                        statusMessage = "Подтверждение не завершено. Можно продолжить ожидание.",
                        errorMessage = error.userMessage("Ошибка авторизации Яндекс Музыки."),
                    )
                    ?: YandexAuthState(
                        statusMessage = "Не удалось подключить Яндекс Музыку.",
                        errorMessage = error.userMessage("Ошибка авторизации Яндекс Музыки."),
                    )
            }
        }
    }

    fun hasPendingAuthorization(): Boolean = tokenStore.loadPending() != null

    fun cancelAuthorization() {
        tokenStore.clearPending()
        mutableAuthState.value = tokenStore.load()?.toAuthState()
            ?: YandexAuthState(statusMessage = "Подключение Яндекс Музыки отменено.")
    }

    fun disconnect() {
        tokenStore.clear()
        tokenStore.clearPending()
        api.accessToken = null
        mutableAuthState.value = YandexAuthState(statusMessage = "Аккаунт Яндекс Музыки отключён.")
    }

    suspend fun download(
        sourceUrl: String,
        progress: (Float, String) -> Unit,
    ): YandexDownloadResult = withContext(Dispatchers.IO) {
        val source = YandexMusicUrlParser.parse(sourceUrl)
        val token = ensureAuthorized()
        api.accessToken = token.accessToken
        progress(0.02f, "Получаем данные из Яндекс Музыки...")
        val collection = YandexCatalogResolver(api).resolve(source)
        val uniqueTracks = collection.tracks
        require(uniqueTracks.isNotEmpty()) { "В коллекции нет доступных треков." }

        val workspace = File(appContext.cacheDir, "luxmusic-yandex-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory) { "Не удалось подготовить папку загрузки." }
        }
        val downloaded = mutableListOf<Pair<YandexTrack, DownloadedTrackImport>>()
        val warnings = mutableListOf<String>()
        val artworkCache = mutableMapOf<String, ByteArray?>()
        try {
            uniqueTracks.forEachIndexed { index, track ->
                val startProgress = 0.05f + (index.toFloat() / uniqueTracks.size) * 0.78f
                progress(startProgress, "Скачиваем ${index + 1} из ${uniqueTracks.size}: ${track.title}")
                runCatching {
                    val downloadedFile = downloadTrack(track, workspace)
                    val artwork = track.coverUri?.let { coverUri ->
                        if (artworkCache.containsKey(coverUri)) {
                            artworkCache[coverUri]
                        } else {
                            downloadCover(coverUri).also { artworkCache[coverUri] = it }
                        }
                    }
                    DownloadedTrackImport(
                        sourceId = track.id.toString(),
                        audioFile = downloadedFile,
                        title = track.title,
                        artist = track.artistNames,
                        album = track.albumTitle ?: "Синглы",
                        durationMs = track.durationMs,
                        artworkBytes = artwork,
                        sourceUrl = sourceUrl,
                    )
                }.onSuccess { item ->
                    downloaded += track to item
                }.onFailure { error ->
                    warnings += "${track.artistNames} — ${track.title}: ${error.userMessage("ошибка загрузки")}".take(350)
                }
            }
            require(downloaded.isNotEmpty()) {
                warnings.firstOrNull() ?: "Не удалось скачать ни одного трека."
            }
            progress(0.86f, "Сохраняем треки в библиотеку...")
            val imported = libraryStore.importDownloadedTracks(downloaded.map { it.second })
            val localBySourceId = imported.associate { it.sourceId to it.track }
            require(localBySourceId.isNotEmpty()) { "Не удалось добавить скачанные файлы в библиотеку." }

            val playlistGroups = collection.albums.mapNotNull { album ->
                val ids = album.tracks.mapNotNull { localBySourceId[it.id.toString()]?.id }.distinct()
                if (ids.isEmpty()) null else YandexPlaylistGroup(album.title, ids)
            }
            progress(1f, "Из Яндекс Музыки сохранено ${localBySourceId.size} трек(ов).")
            YandexDownloadResult(
                tracks = downloaded.mapNotNull { localBySourceId[it.first.id.toString()] },
                collectionLabel = collection.label,
                sourceKind = collection.sourceKind,
                playlistGroups = playlistGroups,
                warnings = warnings,
            )
        } finally {
            runCatching { workspace.deleteRecursively() }
        }
    }

    private fun ensureAuthorized(): StoredYandexToken {
        var stored = tokenStore.load() ?: error("Сначала подключите аккаунт Яндекс Музыки.")
        if (stored.expiresAt != null && stored.expiresAt <= System.currentTimeMillis() + 60_000L) {
            val refreshToken = stored.refreshToken ?: run {
                disconnect()
                error("Сессия Яндекс Музыки истекла. Подключите аккаунт снова.")
            }
            val refreshed = api.refreshToken(refreshToken)
            val account = api.validateAccount(refreshed.accessToken)
            tokenStore.save(refreshed, account)
            stored = tokenStore.load() ?: error("Не удалось сохранить обновлённую сессию.")
        } else {
            try {
                val account = api.validateAccount(stored.accessToken)
                if (stored.accountName != account) tokenStore.updateAccountName(account)
                mutableAuthState.value = stored.copy(accountName = account).toAuthState()
            } catch (error: YandexApiException) {
                if (error.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    disconnect()
                    throw IllegalStateException("Сессия Яндекс Музыки истекла. Подключите аккаунт снова.")
                }
                throw error
            }
        }
        return stored
    }

    private fun downloadTrack(track: YandexTrack, workspace: File): File {
        var lastError: Throwable? = null
        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                val option = YandexDownloadPolicy.selectBestOption(api.downloadOptions(track.id))
                    ?: error("Нет доступного полного аудиофайла.")
                val directUrl = api.resolveDirectLink(option)
                val extension = when (option.codec) {
                    "aac" -> "m4a"
                    else -> "mp3"
                }
                val destination = File(workspace, "${track.id}-$attempt.$extension")
                downloadFile(directUrl, destination, MAX_TRACK_BYTES)
                require(destination.length() > 0L) { "Получен пустой аудиофайл." }
                return destination
            } catch (error: Throwable) {
                lastError = error
                if (attempt + 1 < DOWNLOAD_ATTEMPTS) Thread.sleep(750L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Не удалось скачать трек.")
    }

    private fun downloadCover(rawCoverUri: String): ByteArray? {
        val sizedUrl = YandexDownloadPolicy.coverUrl(rawCoverUri) ?: return null
        return runCatching {
            val temporary = File.createTempFile("lux-cover-", ".img", appContext.cacheDir)
            try {
                downloadFile(sizedUrl, temporary, MAX_COVER_BYTES)
                temporary.readBytes()
            } finally {
                temporary.delete()
            }
        }.getOrNull()
    }

    private fun downloadFile(url: String, destination: File, limit: Long) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.userInfo == null) { "Небезопасная ссылка на файл." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "LuxMusic/0.6 Android")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Сервер вернул HTTP $status." }
            val declared = connection.contentLengthLong
            require(declared < 0L || declared <= limit) { "Файл превышает допустимый размер." }
            var total = 0L
            connection.inputStream.buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= limit) { "Файл превышает допустимый размер." }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun Throwable.userMessage(fallback: String): String = message
        ?.replace(Regex("https?://\\S+"), "[ссылка скрыта]")
        ?.takeIf(String::isNotBlank)
        ?: fallback

    private companion object {
        const val DOWNLOAD_ATTEMPTS = 3
        const val MAX_TRACK_BYTES = 250L * 1024L * 1024L
        const val MAX_COVER_BYTES = 5L * 1024L * 1024L
    }
}

private data class StoredYandexToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long?,
    val accountName: String?,
) {
    fun toAuthState(): YandexAuthState = YandexAuthState(
        isConnected = true,
        accountName = accountName,
        statusMessage = "Аккаунт подключён. Доступны треки, альбомы и дискографии артистов.",
    )
}

private class YandexTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            preferences.edit(commit = true) { putString(KEY_DEVICE_ID, id) }
        }

    fun authState(): YandexAuthState {
        return load()?.toAuthState()
            ?: loadPending()?.toAuthState()
            ?: YandexAuthState()
    }

    fun load(): StoredYandexToken? {
        val raw = preferences.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            StoredYandexToken(
                accessToken = root.getString("accessToken"),
                refreshToken = root.optString("refreshToken").takeIf { it.isNotBlank() },
                expiresAt = root.optLong("expiresAt").takeIf { it > 0L },
                accountName = root.optString("accountName").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    fun save(token: YandexOAuthToken, accountName: String) {
        val expiresAt = token.expiresInSeconds?.let { System.currentTimeMillis() + it * 1_000L }
        val payload = JSONObject()
            .put("accessToken", token.accessToken)
            .putOpt("refreshToken", token.refreshToken)
            .putOpt("expiresAt", expiresAt)
            .put("accountName", accountName)
        preferences.edit(commit = true) { putString(KEY_TOKEN, payload.toString()) }
    }

    fun loadPending(): PendingYandexAuthorization? {
        val raw = preferences.getString(KEY_PENDING, null) ?: return null
        val pending = runCatching {
            val root = JSONObject(raw)
            PendingYandexAuthorization(
                deviceCode = root.getString("deviceCode"),
                userCode = root.getString("userCode"),
                verificationUrl = root.getString("verificationUrl").also { url ->
                    require(YandexOAuthPolicy.isTrustedVerificationUrl(url))
                },
                expiresAtEpochMs = root.getLong("expiresAtEpochMs"),
                intervalSeconds = root.optLong("intervalSeconds", 5L).coerceIn(3L, 30L),
            )
        }.getOrNull()
        if (pending == null || !YandexOAuthPolicy.isPendingUsable(pending.expiresAtEpochMs)) {
            clearPending()
            return null
        }
        return pending
    }

    fun savePending(pending: PendingYandexAuthorization) {
        val payload = JSONObject()
            .put("deviceCode", pending.deviceCode)
            .put("userCode", pending.userCode)
            .put("verificationUrl", pending.verificationUrl)
            .put("expiresAtEpochMs", pending.expiresAtEpochMs)
            .put("intervalSeconds", pending.intervalSeconds)
        preferences.edit(commit = true) { putString(KEY_PENDING, payload.toString()) }
    }

    fun clearPending() {
        preferences.edit(commit = true) { remove(KEY_PENDING) }
    }

    fun updateAccountName(accountName: String) {
        val stored = load() ?: return
        val payload = JSONObject()
            .put("accessToken", stored.accessToken)
            .putOpt("refreshToken", stored.refreshToken)
            .putOpt("expiresAt", stored.expiresAt)
            .put("accountName", accountName)
        preferences.edit(commit = true) { putString(KEY_TOKEN, payload.toString()) }
    }

    fun clear() {
        preferences.edit(commit = true) { remove(KEY_TOKEN) }
    }

    private companion object {
        const val PREFERENCES_NAME = "luxmusic_yandex_oauth"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "oauth_token"
        const val KEY_PENDING = "pending_authorization"
    }
}
