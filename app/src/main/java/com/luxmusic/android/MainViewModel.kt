package com.luxmusic.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val linkDownloader = luxApp.linkDownloader

    private val searchQuery = MutableStateFlow("")
    private val selectedTab = MutableStateFlow(LuxTab.HOME)
    private val messagesFlow = MutableSharedFlow<String>()

    val messages = messagesFlow.asSharedFlow()

    val uiState: StateFlow<LuxMusicUiState> = combine(
        libraryStore.snapshot,
        playbackController.state,
        linkDownloader.state,
        searchQuery,
        selectedTab,
    ) { library, playback, download, query, tab ->
        val visibleTracks = if (query.isBlank()) {
            library.tracks
        } else {
            library.tracks.filter { track ->
                listOf(track.title, track.artist, track.album)
                    .joinToString(" ")
                    .contains(query.trim(), ignoreCase = true)
            }
        }

        LuxMusicUiState(
            library = library.tracks,
            visibleTracks = visibleTracks,
            playlists = library.playlists,
            selectedTab = tab,
            searchQuery = query,
            playback = playback,
            currentTrack = library.tracks.firstOrNull { it.id == playback.currentTrackId },
            download = download,
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
                    "Плейлист \"${removed.name}\" удалён."
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
                    "Трек \"${removed.title}\" удалён с устройства."
                } else {
                    "Не удалось удалить трек."
                },
            )
        }
    }

    fun playTrack(trackId: String) {
        val queue = uiState.value.visibleTracks.ifEmpty { uiState.value.library }
        val index = queue.indexOfFirst { it.id == trackId }
        if (index >= 0) {
            playbackController.playCollection(queue, index, "Библиотека")
        }
    }

    fun playPlaylist(playlistId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        if (queue.isNotEmpty()) {
            playbackController.playCollection(queue, 0, playlist.name)
        }
    }

    fun playPlaylistTrack(playlistId: String, trackId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        val startIndex = queue.indexOfFirst { it.id == trackId }
        if (startIndex >= 0) {
            playbackController.playCollection(queue, startIndex, playlist.name)
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
                messagesFlow.emit("Скачано и сохранено ${imported.size} трек(ов).")
            }.onFailure { error ->
                messagesFlow.emit(error.message ?: "Не удалось скачать музыку по ссылке.")
            }
        }
    }
}
