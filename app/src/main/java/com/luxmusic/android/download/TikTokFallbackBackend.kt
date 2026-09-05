package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder

/** Public TikWM fallback, adapted from nekotyy/tiktok-bot's TikTok pipeline. */
internal class TikTokFallbackBackend(
    private val http: MetadataHttpClient,
    private val mediaBackend: MediaDownloadBackend,
) : MediaDownloadBackend {
    override fun update(channel: ExtractorChannel) = Unit

    override fun fetchInfo(url: String, service: DownloadService, session: DownloadSession?) = null

    override fun download(
        requestUrl: String,
        service: DownloadService,
        session: DownloadSession?,
        outputDir: File,
        onProgress: (Float, String?) -> Unit,
    ) {
        require(DownloadParsing.detectService(requestUrl) == DownloadService.TIKTOK)
        val response = http.getText(
            "https://tikwm.com/api/?url=" + URLEncoder.encode(requestUrl, "UTF-8"),
            mapOf("Accept" to "application/json", "User-Agent" to "LuxMusic/0.7 (Android)"),
        ) ?: error("Резервный сервис TikTok временно недоступен. Повторите попытку позже.")
        val media = parseResponse(response)
        // FFmpeg extracts the clip's audio and also supports a photo post's music URL.
        mediaBackend.download(media.url, DownloadService.UNKNOWN, null, outputDir, onProgress)
        outputDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .forEach { audio ->
                File(outputDir, "${audio.nameWithoutExtension}.info.json").writeText(
                    JSONObject().put("title", media.title).put("uploader", media.artist).toString(),
                )
            }
    }

    internal data class Media(val url: String, val title: String, val artist: String)

    companion object {
        internal fun parseResponse(response: String): Media {
            val root = JSONObject(response)
            check(root.optInt("code", -1) == 0) { "TikTok не вернул доступное аудио. Проверьте ссылку." }
            val data = root.optJSONObject("data") ?: error("TikTok вернул пустой ответ.")
            val music = data.optJSONObject("music_info")
            val isPhoto = (data.optJSONArray("images")?.length() ?: 0) > 0
            val rawUrl = if (isPhoto) music?.text("play") ?: data.text("music") else
                data.text("play") ?: data.text("wmplay") ?: music?.text("play") ?: data.text("music")
            check(!rawUrl.isNullOrBlank()) { "В публикации TikTok нет доступной звуковой дорожки." }
            val url = when {
                rawUrl.startsWith("//") -> "https:$rawUrl"
                rawUrl.startsWith("/") -> "https://tikwm.com$rawUrl"
                else -> rawUrl
            }
            val uri = URI(url)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                "TikTok вернул некорректную ссылку на медиа."
            }
            val author = data.optJSONObject("author")
            return Media(
                url,
                data.text("title") ?: music?.text("title") ?: "TikTok Audio",
                author?.text("nickname") ?: author?.text("unique_id") ?: music?.text("author") ?: "TikTok",
            )
        }

        private fun JSONObject.text(key: String): String? = (opt(key) as? String)?.takeIf(String::isNotBlank)
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "opus", "ogg", "aac", "wav", "flac", "webm", "mp4")
    }
}
