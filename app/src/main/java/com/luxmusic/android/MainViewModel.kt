package com.luxmusic.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luxmusic.android.data.DownloadAccountState
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LuxTab {
    HOME,
    LIBRARY,
    PLAYLISTS,
    DOWNLOAD,
}

data class LuxMusicUiState(
    val library: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloadAccounts: List<DownloadAccountState> = emptyList(),
    val selectedTab: LuxTab = LuxTab.HOME,
    val searchQuery: String = "",
    val playback: PlaybackState = PlaybackState(),
    val currentTrack: Track? = null,
    val download: DownloadState = DownloadState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val luxApp = application as LuxMusicApp
    private val libraryStore = luxApp.libraryStore
    private val playbackController = luxApp.playbackController
    private val downloadAccountStore = luxApp.downloadAccountStore
    private val linkDownloader = luxApp.linkDownloader

    private val searchQuery = MutableStateFlow("")
    private val selectedTab = MutableStateFlow(LuxTab.HOME)
    private val messagesFlow = MutableSharedFlow<String>()

    val messages = messagesFlow.asSharedFlow()

    val uiState: StateFlow<LuxMusicUiState> = combine(
        libraryStore.snapshot,
        playbackController.state,
        downloadAccountStore.accounts,
        linkDownloader.state,
        searchQuery,
    ) { library, playback, downloadAccounts, download, query ->
        CombinedUiInputs(
            library = library,
            playback = playback,
            downloadAccounts = downloadAccounts,
            download = download,
            query = query,
        )
    }.combine(selectedTab) { inputs, tab ->
        val visibleTracks = if (inputs.query.isBlank()) {
            inputs.library.tracks
        } else {
            inputs.library.tracks.filter { track ->
                listOf(track.title, track.artist, track.album)
                    .joinToString(" ")
                    .contains(inputs.query.trim(), ignoreCase = true)
            }
        }

        LuxMusicUiState(
            library = inputs.library.tracks,
            visibleTracks = visibleTracks,
            playlists = inputs.library.playlists,
            downloadAccounts = inputs.downloadAccounts,
            selectedTab = tab,
            searchQuery = inputs.query,
            playback = inputs.playback,
            currentTrack = inputs.library.tracks.firstOrNull { it.id == inputs.playback.currentTrackId },
            download = inputs.download,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LuxMusicUiState(),
    )

    fun selectTab(tab: LuxTab) {
        selectedTab.value = tab
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun importAudio(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val imported = libraryStore.importUris(uris)
            if (imported.isNotEmpty()) {
                selectedTab.value = LuxTab.LIBRARY
            }
            messagesFlow.emit(
                if (imported.isEmpty()) {
                    "Не удалось импортировать выбранные файлы."
                } else {
                    "Добавлено ${imported.size} трек(ов) в локальную библиотеку."
                },
            )
        }
    }

    fun createPlaylist(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            libraryStore.createPlaylist(normalized)
            selectedTab.value = LuxTab.PLAYLISTS
            messagesFlow.emit("Плейлист \"$normalized\" создан.")
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            libraryStore.addTrackToPlaylist(playlistId, trackId)
            val playlistName = libraryStore.snapshot.value.playlists.firstOrNull { it.id == playlistId }?.name
            messagesFlow.emit(
                if (playlistName != null) {
                    "Трек добавлен в \"$playlistName\"."
                } else {
                    "Трек добавлен в плейлист."
                },
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val removed = libraryStore.deletePlaylist(playlistId)
            messagesFlow.emit(
                if (removed != null) {
                    "Плейлист \"${removed.name}\" удален."
                } else {
                    "Не удалось удалить плейлист."
                },
            )
        }
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            playbackController.removeTrack(trackId)
            val removed = libraryStore.deleteTrack(trackId)
            messagesFlow.emit(
                if (removed != null) {
                    "Трек \"${removed.title}\" удален с устройства."
                } else {
                    "Не удалось удалить трек."
                },
            )
        }
    }

    fun toggleLibraryTrack(trackId: String) {
        val queue = uiState.value.visibleTracks.ifEmpty { uiState.value.library }
        val index = queue.indexOfFirst { it.id == trackId }
        if (index >= 0) {
            playbackController.playOrToggleCollection(queue, index, "Библиотека")
        }
    }

    fun playTrack(trackId: String) = toggleLibraryTrack(trackId)

    fun playPlaylist(playlistId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        if (queue.isNotEmpty()) {
            playbackController.playOrToggleCollection(queue, 0, playlist.name)
        }
    }

    fun playPlaylistTrack(playlistId: String, trackId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        val startIndex = queue.indexOfFirst { it.id == trackId }
        if (startIndex >= 0) {
            playbackController.playOrToggleCollection(queue, startIndex, playlist.name)
        }
    }

    fun togglePlayback() = playbackController.togglePlayback()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeat() = playbackController.cycleRepeatMode()

    fun seekToFraction(fraction: Float) = playbackController.seekToFraction(fraction)

    fun downloadFromLink(url: String) {
        val normalized = url.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val result = linkDownloader.download(normalized)
            result.onSuccess { imported ->
                selectedTab.value = LuxTab.LIBRARY
                messagesFlow.emit("Скачано и сохранено ${imported.size} трек(ов).")
            }.onFailure { error ->
                messagesFlow.emit(error.message ?: "Не удалось скачать музыку по ссылке.")
            }
        }
    }

    fun importDownloadAccountCookies(service: DownloadService, uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            val result = downloadAccountStore.importCookies(service, uri)
            result.onSuccess {
                messagesFlow.emit("Аккаунт ${service.title} подключен через cookies.txt.")
            }.onFailure { error ->
                messagesFlow.emit(error.message ?: "Не удалось импортировать cookies.txt для ${service.title}.")
            }
        }
    }

    fun captureDownloadAccountCookies(service: DownloadService, userAgent: String?) {
        viewModelScope.launch {
            val result = downloadAccountStore.captureCookiesFromWebView(service, userAgent)
            result.onSuccess {
                messagesFlow.emit("Аккаунт ${service.title} подключен.")
            }.onFailure { error ->
                messagesFlow.emit(error.message ?: "Не удалось завершить вход для ${service.title}.")
            }
        }
    }

    fun clearDownloadAccount(service: DownloadService) {
        viewModelScope.launch {
            downloadAccountStore.clearSession(service)
            messagesFlow.emit("Сессия ${service.title} отключена.")
        }
    }

    private data class CombinedUiInputs(
        val library: com.luxmusic.android.data.LibrarySnapshot,
        val playback: PlaybackState,
        val downloadAccounts: List<DownloadAccountState>,
        val download: DownloadState,
        val query: String,
    )
}
