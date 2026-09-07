package com.luxmusic.android.download

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

internal enum class RemoteFileKind { AUDIO, ZIP }
internal data class RemoteFileResult(val file: File, val kind: RemoteFileKind)
internal class RemoteFileHttpException(val status: Int) : IOException("Сервер вернул HTTP $status при скачивании файла.")

/** Probe and stream in the same GET: no HEAD, second download or native runtime. */
internal class RemoteFileTransfer(
    private val maximumBytes: Long = 1_024L * 1_024L * 1_024L,
    private val timeoutMs: Int = 12_000,
) {
    fun downloadIfMedia(url: String, directory: File, onProgress: (Float) -> Unit): RemoteFileResult? {
        val connection = open(url)
        try {
            val length = connection.contentLengthLong
            val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            val disposition = connection.getHeaderField("Content-Disposition")
            val name = responseFileName(disposition, connection.url)
            connection.inputStream.buffered(BUFFER_BYTES).use { input ->
                val prefix = ByteArray(64)
                var prefixSize = 0
                while (prefixSize < prefix.size) {
                    checkInterrupted()
                    val read = input.read(prefix, prefixSize, prefix.size - prefixSize)
                    if (read < 0) break
                    prefixSize += read
                }
                val bytes = prefix.copyOf(prefixSize)
                val zip = RemoteDownloadClassifier.hasZipSignature(bytes)
                val sniffed = audioExtension(bytes)
                val declared = name.substringAfterLast('.', "").lowercase()
                    .takeIf { it in RemoteDownloadClassifier.audioExtensions }
                val textPrefix = bytes.toString(Charsets.UTF_8).trimStart().lowercase()
                val looksLikePage = type.contains("html") || type.contains("json") ||
                    textPrefix.startsWith("<") || textPrefix.startsWith("{") || textPrefix.startsWith("[")
                val extension = if (zip) "zip" else if (!looksLikePage)
                    sniffed ?: mimeExtension(type) ?: declared
                else null
                if (extension == null) return null
                if (length > maximumBytes) throw IOException("Файл больше допустимого размера 1 ГБ.")
                if (prefixSize == 0) throw IOException("Сервер вернул пустой файл.")

                val stem = name.substringBeforeLast('.', name).replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), "_")
                    .trim().trim('.').take(100).ifBlank { "Audio" }
                val file = File(directory, "$stem.$extension")
                try {
                    var total = prefixSize.toLong()
                    if (total > maximumBytes) throw IOException("Файл превышает допустимый размер.")
                    val limiter = DownloadProgressLimiter()
                    file.outputStream().buffered(BUFFER_BYTES).use { output ->
                        output.write(bytes)
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            checkInterrupted()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maximumBytes) throw IOException("Файл больше допустимого размера 1 ГБ.")
                            output.write(buffer, 0, read)
                            val progress = if (length > 0) (total.toFloat() / length).coerceIn(0f, 1f) else 0f
                            if (limiter.shouldPublish(progress)) onProgress(progress)
                        }
                    }
                    if (length >= 0 && total != length) throw IOException("Загрузка оборвалась: получен неполный файл.")
                    onProgress(1f)
                    return RemoteFileResult(file, if (zip) RemoteFileKind.ZIP else RemoteFileKind.AUDIO)
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
            }
        } finally { connection.disconnect() }
    }

    private fun open(value: String): HttpURLConnection {
        var url = URL(value)
        repeat(6) { hop ->
            require(url.protocol.lowercase() in setOf("http", "https") && url.userInfo == null) {
                "Нужна HTTP(S)-ссылка без логина и пароля в адресе."
            }
            checkInterrupted()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", "LuxMusic/0.7.1 (Android)")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Пустое перенаправление сервера.")
                    val target = URL(url, location)
                    if (url.protocol == "https" && target.protocol != "https")
                        throw IOException("Сервер перенаправил защищённую ссылку на небезопасное соединение.")
                    if (hop == 5) throw IOException("Слишком много перенаправлений ссылки.")
                    url = target
                    connection.disconnect()
                } else {
                    if (status !in 200..299) throw RemoteFileHttpException(status)
                    if (status == 206) throw IOException("Сервер вернул только часть файла без запроса диапазона.")
                    return connection
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        throw IOException("Слишком много перенаправлений ссылки.")
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Загрузка отменена.")
    }

    companion object {
        private const val BUFFER_BYTES = 128 * 1_024

        internal fun responseFileName(disposition: String?, url: URL): String {
            val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                .find(disposition.orEmpty())?.groupValues?.get(1)
            val plain = Regex("filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))", RegexOption.IGNORE_CASE)
                .find(disposition.orEmpty())?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }
            val raw = encoded?.let { runCatching { URLDecoder.decode(it.trim().replace("+", "%2B"), "UTF-8") }.getOrNull() }
                ?: plain?.trim() ?: runCatching { URLDecoder.decode(url.path.replace("+", "%2B"), "UTF-8") }.getOrDefault("Audio")
            return raw.replace('\\', '/').substringAfterLast('/').ifBlank { "Audio" }
        }

        internal fun audioExtension(bytes: ByteArray): String? {
            fun marker(offset: Int, text: String) = bytes.size >= offset + text.length &&
                text.indices.all { bytes[offset + it] == text[it].code.toByte() }
            return when {
                marker(0, "RIFF") && marker(8, "WAVE") -> "wav"
                marker(0, "fLaC") -> "flac"
                marker(0, "OggS") -> if (bytes.toString(Charsets.ISO_8859_1).contains("OpusHead")) "opus" else "ogg"
                marker(4, "ftyp") && (marker(8, "M4A ") || marker(8, "M4B ") || marker(8, "f4a ")) -> "m4a"
                marker(0, "ID3") -> "mp3"
                bytes.size >= 2 && bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0 ->
                    if (bytes[1].toInt() and 0xf6 == 0xf0) "aac" else "mp3"
                else -> null
            }
        }

        private fun mimeExtension(type: String): String? = when (type) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/aac", "audio/aacp" -> "aac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/wave", "audio/x-wav" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            else -> null
        }
    }
}
