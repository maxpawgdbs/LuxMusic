package com.luxmusic.android.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MetadataExtractor(private val context: Context) {
    fun fromFile(file: File, companionFiles: List<File> = emptyList()): ExtractedTrackMetadata {
        val retriever = MediaMetadataRetriever()
        val availableCompanions = buildCompanionList(file, companionFiles)
        val infoMetadata = findInfoJsonFile(file, availableCompanions)?.let(::readInfoMetadata)

        return try {
            retriever.setDataSource(file.absolutePath)
            val artworkBytes = retriever.embeddedPicture ?: findArtworkFile(file, availableCompanions)?.readBytes()
            val lyrics = findLyricsFile(file, availableCompanions)?.readText()?.let(::normalizeLyrics)
            val retrieverDurationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }

            ExtractedTrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).normalizedOrNull()
                    ?: infoMetadata?.title,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).normalizedOrNull()
                    ?: infoMetadata?.artist,
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).normalizedOrNull()
                    ?: infoMetadata?.album,
                durationMs = retrieverDurationMs
                    ?: extractDurationWithMediaExtractor(file)
                    ?: infoMetadata?.durationMs
                    ?: 0L,
                artworkBytes = artworkBytes,
                lyrics = lyrics,
            )
        } finally {
            retriever.release()
        }
    }

    fun probeDurationMs(file: File, companionFiles: List<File> = emptyList()): Long {
        return fromFile(file, companionFiles).durationMs
    }

    private fun buildCompanionList(file: File, companionFiles: List<File>): List<File> {
        val siblings = file.parentFile?.listFiles()?.toList().orEmpty()
        return (siblings + companionFiles)
            .filter { it.exists() && it.isFile }
            .distinctBy { it.absolutePath }
    }

    private fun findArtworkFile(file: File, candidates: List<File>): File? {
        val baseName = file.nameWithoutExtension.lowercase()
        return candidates.firstOrNull { candidate ->
            val name = candidate.nameWithoutExtension.lowercase()
            candidate.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") &&
                (name == baseName || name == "cover" || name == "folder" || name.startsWith(baseName))
        }
    }

    private fun findLyricsFile(file: File, candidates: List<File>): File? {
        val baseName = file.nameWithoutExtension.lowercase()
        return candidates.firstOrNull { candidate ->
            val name = candidate.nameWithoutExtension.lowercase()
            candidate.extension.lowercase() in setOf("lrc", "txt", "vtt") &&
                (name == baseName || name == "lyrics" || name.startsWith(baseName))
        }
    }

    private fun findInfoJsonFile(file: File, candidates: List<File>): File? {
        val baseName = file.nameWithoutExtension.lowercase()
        return candidates.firstOrNull { candidate ->
            val normalizedName = candidate.name.lowercase()
            candidate.extension.lowercase() == "json" &&
                normalizedName.endsWith(".info.json") &&
                (
                    candidate.nameWithoutExtension.lowercase() == "$baseName.info" ||
                        candidate.nameWithoutExtension.lowercase().startsWith(baseName)
                    )
        }
    }

    private fun readInfoMetadata(file: File): InfoMetadata? {
        return runCatching {
            val root = JSONObject(file.readText())
            InfoMetadata(
                title = root.optStringOrNull("track")
                    ?: root.optStringOrNull("title")
                    ?: root.optStringOrNull("fulltitle"),
                artist = root.optStringOrNull("artist")
                    ?: root.optStringOrNull("creator")
                    ?: firstArtist(root.optJSONArray("artists"))
                    ?: root.optStringOrNull("uploader")
                    ?: root.optStringOrNull("channel"),
                album = root.optStringOrNull("album")
                    ?: root.optStringOrNull("playlist_title")
                    ?: root.optStringOrNull("playlist"),
                durationMs = root.optDouble("duration")
                    .takeIf { it.isFinite() && it > 0.0 }
                    ?.times(1_000)
                    ?.toLong(),
            )
        }.getOrNull()
    }

    private fun extractDurationWithMediaExtractor(file: File): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .mapNotNull { index ->
                    val format = extractor.getTrackFormat(index)
                    val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (!mimeType.startsWith("audio/")) {
                        return@mapNotNull null
                    }

                    if (!format.containsKey(MediaFormat.KEY_DURATION)) {
                        return@mapNotNull null
                    }

                    format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }?.div(1_000)
                }
                .maxOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun normalizeLyrics(raw: String): String {
        val cleaned = raw.lineSequence()
            .map(String::trim)
            .filter { line ->
                line.isNotBlank() &&
                    line != "WEBVTT" &&
                    !line.matches(Regex("^\\d+$")) &&
                    !line.matches(Regex("^\\d{2}:\\d{2}[:.,]\\d{2,3}.*")) &&
                    !line.matches(Regex("^\\[\\d{2}:\\d{2}([:.]\\d{2})?].*"))
            }
            .joinToString("\n")
            .trim()

        return cleaned.ifBlank { raw.trim() }
    }

    private fun firstArtist(artists: JSONArray?): String? {
        if (artists == null) return null

        for (index in 0 until artists.length()) {
            val value = artists.opt(index)
            when (value) {
                is String -> value.normalizedOrNull()?.let { return it }
                is JSONObject -> value.optStringOrNull("name")?.let { return it }
            }
        }

        return null
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun JSONObject.optStringOrNull(key: String): String? = optString(key).normalizedOrNull()

    private data class InfoMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
    )
}
