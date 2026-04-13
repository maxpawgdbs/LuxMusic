package com.luxmusic.android.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

class MetadataExtractor(private val context: Context) {
    fun fromFile(file: File, companionFiles: List<File> = emptyList()): ExtractedTrackMetadata {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(file.absolutePath)
            val availableCompanions = buildCompanionList(file, companionFiles)
            val artworkBytes = retriever.embeddedPicture ?: findArtworkFile(file, availableCompanions)?.readBytes()
            val lyrics = findLyricsFile(file, availableCompanions)?.readText()?.let(::normalizeLyrics)

            ExtractedTrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                artworkBytes = artworkBytes,
                lyrics = lyrics,
            )
        } finally {
            retriever.release()
        }
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
}

