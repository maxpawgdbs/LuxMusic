package com.luxmusic.android.download.yandex

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class YandexDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

data class YandexOAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

data class YandexDownloadOption(
    val codec: String,
    val bitrateKbps: Int,
    val preview: Boolean,
    val downloadInfoUrl: String,
)

data class YandexAuthState(
    val isConnected: Boolean = false,
    val isAuthorizing: Boolean = false,
    val accountName: String? = null,
    val verificationUrl: String? = null,
    val userCode: String? = null,
    val statusMessage: String = "Подключите Яндекс Музыку, чтобы скачивать треки, альбомы и артистов.",
    val errorMessage: String? = null,
)

class YandexApiException(
    val statusCode: Int,
    val errorCode: String? = null,
    message: String,
) : IllegalStateException(message)

class YandexMusicHttpClient : YandexCatalogApi {
    @Volatile
    var accessToken: String? = null

    fun requestDeviceCode(deviceId: String, deviceName: String): YandexDeviceCode {
        val response = requestJson(
            method = "POST",
            url = "$OAUTH_BASE_URL/device/code",
            form = mapOf(
                "client_id" to CLIENT_ID,
                "device_id" to deviceId,
                "device_name" to deviceName,
            ),
            includeMusicHeaders = false,
        )
        return YandexDeviceCode(
            deviceCode = response.requiredString("device_code"),
            userCode = response.requiredString("user_code"),
            verificationUrl = response.requiredString("verification_url").also { url ->
                require(YandexOAuthPolicy.isTrustedVerificationUrl(url)) {
                    "Сервис вернул некорректную страницу авторизации."
                }
            },
            expiresInSeconds = response.optLong("expires_in", 600L).coerceIn(60L, 1_800L),
            intervalSeconds = response.optLong("interval", 5L).coerceIn(3L, 30L),
        )
    }

    fun pollDeviceToken(deviceCode: String): YandexOAuthToken? {
        val response = try {
            requestJson(
                method = "POST",
                url = "$OAUTH_BASE_URL/token",
                form = mapOf(
                    "grant_type" to "device_code",
                    "code" to deviceCode,
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                ),
                includeMusicHeaders = false,
            )
        } catch (error: YandexApiException) {
            if (YandexOAuthPolicy.isAuthorizationPending(error.errorCode, error.message)) return null
            throw error
        }
        return YandexOAuthToken(
            accessToken = response.requiredString("access_token"),
            refreshToken = response.nullableString("refresh_token"),
            expiresInSeconds = response.optLong("expires_in").takeIf { it > 0L },
        )
    }

    fun refreshToken(refreshToken: String): YandexOAuthToken {
        val response = requestJson(
            method = "POST",
            url = "$OAUTH_BASE_URL/token",
            form = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
            ),
            includeMusicHeaders = false,
        )
        return YandexOAuthToken(
            accessToken = response.requiredString("access_token"),
            refreshToken = response.nullableString("refresh_token") ?: refreshToken,
            expiresInSeconds = response.optLong("expires_in").takeIf { it > 0L },
        )
    }

    fun validateAccount(token: String): String {
        accessToken = token
        val result = requestApiJson("GET", "$API_BASE_URL/account/status") as? JSONObject
            ?: error("Яндекс Музыка вернула пустой профиль.")
        val account = result.optJSONObject("account")
            ?: error("Не удалось прочитать профиль Яндекс Музыки.")
        return account.nullableString("displayName")
            ?: account.nullableString("fullName")
            ?: account.nullableString("login")
            ?: "Аккаунт Яндекс"
    }

    override suspend fun track(id: Long): YandexTrack? {
        val result = requestApiJson(
            method = "POST",
            url = "$API_BASE_URL/tracks",
            form = mapOf("track-ids" to id.toString(), "with-positions" to "true"),
        ) as? JSONArray ?: return null
        return result.optJSONObject(0)?.toTrack()
    }

    override suspend fun albumWithTracks(id: Long): YandexAlbum? {
        val result = requestApiJson("GET", "$API_BASE_URL/albums/$id/with-tracks") as? JSONObject
            ?: return null
        return result.toAlbum(includeTracks = true)
    }

    override suspend fun artist(id: Long): YandexArtist? {
        val result = requestApiJson(
            method = "POST",
            url = "$API_BASE_URL/artists",
            form = mapOf("artist-ids" to id.toString()),
        ) as? JSONArray ?: return null
        return result.optJSONObject(0)?.toArtist()
    }

    override suspend fun directAlbums(artistId: Long, page: Int, pageSize: Int): YandexAlbumPage? {
        val result = requestApiJson(
            method = "GET",
            url = "$API_BASE_URL/artists/$artistId/direct-albums",
            query = mapOf(
                "sort-by" to "year",
                "page" to page.toString(),
                "page-size" to pageSize.toString(),
            ),
        ) as? JSONObject ?: return null
        val albums = result.optJSONArray("albums").objects().map { it.toAlbum(includeTracks = false) }
        val pager = result.optJSONObject("pager")
        return YandexAlbumPage(
            albums = albums,
            page = pager?.optInt("page", page) ?: page,
            perPage = pager?.let { it.optInt("perPage", it.optInt("per_page", pageSize)) } ?: pageSize,
            total = pager?.optInt("total", albums.size) ?: albums.size,
        )
    }

    fun downloadOptions(trackId: Long): List<YandexDownloadOption> {
        val result = requestApiJson("GET", "$API_BASE_URL/tracks/$trackId/download-info") as? JSONArray
            ?: return emptyList()
        return result.objects().mapNotNull { item ->
            val url = item.nullableString("downloadInfoUrl") ?: return@mapNotNull null
            YandexDownloadOption(
                codec = item.nullableString("codec").orEmpty().lowercase(),
                bitrateKbps = item.optInt("bitrateInKbps", 0),
                preview = item.optBoolean("preview", false),
                downloadInfoUrl = url,
            )
        }
    }

    fun resolveDirectLink(option: YandexDownloadOption): String {
        require(YandexDownloadPolicy.isTrustedYandexUrl(option.downloadInfoUrl)) {
            "Яндекс вернул небезопасную ссылку."
        }
        val xml = requestBytes("GET", option.downloadInfoUrl, token = accessToken)
            .toString(Charsets.UTF_8)
        return YandexDownloadPolicy.buildDirectLink(xml)
    }

    private fun requestApiJson(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
    ): Any? {
        val token = accessToken ?: error("Сначала подключите аккаунт Яндекс Музыки.")
        val response = requestJson(method, url, query, form, token = token)
        return if (response.has("result") && !response.isNull("result")) response.get("result") else response
    }

    private fun requestJson(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        token: String? = null,
        includeMusicHeaders: Boolean = true,
    ): JSONObject {
        val bytes = requestBytes(method, appendQuery(url, query), form, token, includeMusicHeaders)
        return runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalStateException("Сервис вернул некорректный ответ.", it) }
    }

    private fun requestBytes(
        method: String,
        url: String,
        form: Map<String, String> = emptyMap(),
        token: String? = null,
        includeMusicHeaders: Boolean = true,
    ): ByteArray {
        var lastError: Throwable? = null
        repeat(REQUEST_ATTEMPTS) { attempt ->
            try {
                return requestBytesOnce(method, url, form, token, includeMusicHeaders)
            } catch (error: YandexApiException) {
                if (error.statusCode != 429 && error.statusCode < 500) throw error
                lastError = error
            } catch (error: IOException) {
                lastError = error
            }
            if (attempt + 1 < REQUEST_ATTEMPTS) Thread.sleep(500L * (1 shl attempt))
        }
        throw lastError ?: IOException("Не удалось выполнить запрос к Яндекс Музыке.")
    }

    private fun requestBytesOnce(
        method: String,
        url: String,
        form: Map<String, String>,
        token: String?,
        includeMusicHeaders: Boolean,
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, application/xml, */*")
            setRequestProperty("User-Agent", USER_AGENT)
            if (includeMusicHeaders) {
                setRequestProperty("X-Yandex-Music-Client", MUSIC_CLIENT_HEADER)
                setRequestProperty("Accept-Language", "ru")
            }
            token?.let { setRequestProperty("Authorization", "OAuth $it") }
            if (form.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }
        try {
            if (form.isNotEmpty()) {
                connection.outputStream.use { it.write(encodeForm(form).toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytesLimited(MAX_RESPONSE_BYTES) } ?: ByteArray(0)
            if (status !in 200..299) {
                val text = body.toString(Charsets.UTF_8)
                val parsed = runCatching { JSONObject(text) }.getOrNull()
                val errorCode = parsed?.nullableString("error")
                val reason = parsed?.nullableString("error_description")
                    ?: parsed?.nullableString("errorDescription")
                    ?: errorCode
                    ?: "HTTP $status"
                throw YandexApiException(status, errorCode, reason.take(300))
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun appendQuery(url: String, query: Map<String, String>): String {
        if (query.isEmpty()) return url
        return "$url?${encodeForm(query)}"
    }

    private fun encodeForm(values: Map<String, String>): String = values.entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Ответ сервиса слишком большой." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun JSONObject.toArtist(): YandexArtist? {
        val id = longId("id") ?: return null
        return YandexArtist(id, nullableString("name") ?: "Исполнитель $id")
    }

    private fun JSONObject.toTrack(): YandexTrack? {
        val id = longId("id") ?: return null
        val artists = optJSONArray("artists").objects().mapNotNull { it.toArtist() }
        val album = optJSONArray("albums")?.optJSONObject(0)
        return YandexTrack(
            id = id,
            title = nullableString("title") ?: "Трек $id",
            artists = artists,
            albumId = album?.longId("id"),
            albumTitle = album?.nullableString("title"),
            durationMs = optLong("durationMs", 0L).coerceAtLeast(0L),
            coverUri = nullableString("coverUri") ?: album?.nullableString("coverUri"),
            available = if (has("available")) optBoolean("available") else true,
        )
    }

    private fun JSONObject.toAlbum(includeTracks: Boolean): YandexAlbum {
        val id = longId("id") ?: error("Альбом без идентификатора.")
        val tracks = if (includeTracks) {
            optJSONArray("volumes").arrays().flatMap { volume -> volume.objects().mapNotNull { it.toTrack() } }
        } else {
            emptyList()
        }
        return YandexAlbum(
            id = id,
            title = nullableString("title") ?: "Альбом $id",
            type = nullableString("type"),
            artistIds = optJSONArray("artists").objects().mapNotNull { it.longId("id") },
            tracks = tracks,
        )
    }

    private fun JSONObject.longId(name: String): Long? {
        val value = opt(name) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }?.takeIf { it > 0L }
    }

    private fun JSONObject.requiredString(name: String): String = nullableString(name)
        ?: error("В ответе сервиса отсутствует поле $name.")

    private fun JSONObject.nullableString(name: String): String? = optString(name)
        .trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private fun JSONArray?.arrays(): List<JSONArray> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optJSONArray(index)?.let(::add)
    }

    private companion object {
        const val API_BASE_URL = "https://api.music.yandex.net"
        const val OAUTH_BASE_URL = "https://oauth.yandex.ru"
        const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
        const val CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"
        const val MUSIC_CLIENT_HEADER = "YandexMusicAndroid/24023621"
        const val USER_AGENT = "LuxMusic/0.6 Android"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        const val REQUEST_ATTEMPTS = 3
    }
}

object YandexOAuthPolicy {
    fun isTrustedVerificationUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            uri.host?.lowercase() in setOf("oauth.yandex.ru", "oauth.yandex.com", "ya.ru", "www.ya.ru")
    }

    fun isAuthorizationPending(errorCode: String?, description: String?): Boolean {
        return errorCode.equals("authorization_pending", ignoreCase = true) ||
            description.orEmpty().contains("not yet authorized", ignoreCase = true) ||
            description.orEmpty().contains("authorization_pending", ignoreCase = true)
    }
}
