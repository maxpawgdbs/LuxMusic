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
    val statusMessage: String = "Вставьте ссылку на трек или релиз. LuxMusic попробует сохранить аудио локально на устройстве.",
    val errorMessage: String? = null,
)

enum class DownloadService(
    val title: String,
    val loginUrl: String,
    val cookieDomains: List<String>,
    val requiresAccount: Boolean,
    val accountRecommended: Boolean = false,
) {
    YOUTUBE(
        title = "YouTube",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube",
        cookieDomains = listOf("youtube.com", "music.youtube.com", "google.com"),
        requiresAccount = false,
        accountRecommended = true,
    ),
    YANDEX_MUSIC(
        title = "Яндекс Музыка",
        loginUrl = "https://passport.yandex.ru/auth?retpath=https%3A%2F%2Fmusic.yandex.ru%2F",
        cookieDomains = listOf("music.yandex.ru", "yandex.ru"),
        requiresAccount = true,
    ),
    VK_MUSIC(
        title = "VK Музыка",
        loginUrl = "https://id.vk.com/auth",
        cookieDomains = listOf("vk.com", "vk.ru"),
        requiresAccount = true,
    ),
    APPLE_MUSIC(
        title = "Apple Music",
        loginUrl = "https://music.apple.com/login",
        cookieDomains = listOf("music.apple.com", "apple.com"),
        requiresAccount = true,
    ),
    SPOTIFY(
        title = "Spotify",
        loginUrl = "https://accounts.spotify.com/login",
        cookieDomains = listOf("spotify.com"),
        requiresAccount = true,
    ),
    SOUNDCLOUD(
        title = "SoundCloud",
        loginUrl = "https://soundcloud.com/signin",
        cookieDomains = listOf("soundcloud.com"),
        requiresAccount = false,
    ),
    TIKTOK(
        title = "TikTok",
        loginUrl = "https://www.tiktok.com/login",
        cookieDomains = listOf("tiktok.com"),
        requiresAccount = false,
    ),
    UNKNOWN(
        title = "Неизвестный сервис",
        loginUrl = "https://www.google.com",
        cookieDomains = emptyList(),
        requiresAccount = false,
    ),
}

data class DownloadAccountState(
    val service: DownloadService,
    val isConnected: Boolean,
    val updatedAt: Long? = null,
)

data class ExtractedTrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkBytes: ByteArray? = null,
    val lyrics: String? = null,
)
