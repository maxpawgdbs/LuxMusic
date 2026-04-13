package com.luxmusic.android.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val localPath: String,
    val artworkPath: String? = null,
    val lyrics: String? = null,
    val sourceUrl: String? = null,
    val importedAt: Long,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long,
)

data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

enum class RepeatMode {
    NONE,
    ALL,
    ONE,
}

data class PlaybackState(
    val currentTrackId: String? = null,
    val queueTrackIds: List<String> = emptyList(),
    val queueTitle: String = "Библиотека",
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

data class DownloadState(
    val isAvailable: Boolean = true,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "Вставьте ссылку на музыку, и LuxMusic сохранит её на устройстве.",
    val errorMessage: String? = null,
)

data class ExtractedTrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkBytes: ByteArray? = null,
    val lyrics: String? = null,
)
