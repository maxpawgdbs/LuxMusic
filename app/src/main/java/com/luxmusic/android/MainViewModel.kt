package com.luxmusic.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luxmusic.android.data.DownloadState
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.PlaylistDraft
import com.luxmusic.android.data.Track
import com.luxmusic.android.download.DownloadParsing
import com.luxmusic.android.download.DownloadCollectionResult
import com.luxmusic.android.download.yandex.YandexAuthState
import com.luxmusic.android.download.yandex.YandexAuthorizationService
import com.luxmusic.android.download.yandex.YandexSourceKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LuxTab {
    HOME,
    LIBRARY,
    ARTISTS,
    PLAYLISTS,
    DOWNLOAD,
    SETTINGS,
}

data class LuxMusicUiState(
    val library: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artistArtworkPaths: Map<String, String> = emptyMap(),
    val selectedTab: LuxTab = LuxTab.HOME,
    val searchQuery: String = "",
    val downloadUrl: String = "",
    val downloadTitle: String = "",
    val playback: PlaybackState = PlaybackState(),
    val currentTrack: Track? = null,
    val download: DownloadState = DownloadState(),
    val yandexAuth: YandexAuthState = YandexAuthState(),
)

data class YandexAuthBrowserRequest(
    val url: String,
    val userCode: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val luxApp = application as LuxMusicApp
    private val libraryStore = luxApp.libraryStore
    private val playbackGateway = luxApp.playbackGateway
    private val linkDownloader = luxApp.linkDownloader

    private val searchQuery = MutableStateFlow("")
    private val downloadUrl = MutableStateFlow("")
    private val downloadTitle = MutableStateFlow("")
    private val selectedTab = MutableStateFlow(LuxTab.HOME)
    private val messagesFlow = luxApp.messages
    private val authBrowserRequestsFlow = Channel<YandexAuthBrowserRequest>(Channel.BUFFERED)

    val messages = messagesFlow.events
    val authBrowserRequests = authBrowserRequestsFlow.receiveAsFlow()

    val uiState: StateFlow<LuxMusicUiState> = combine(
        libraryStore.snapshot,
        playbackGateway.state,
        linkDownloader.state,
        searchQuery,
    ) { library, playback, download, query ->
        CombinedUiInputs(
            library = library,
            playback = playback,
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
            artistArtworkPaths = inputs.library.artistArtworkPaths,
            selectedTab = tab,
            searchQuery = inputs.query,
            playback = inputs.playback,
            currentTrack = inputs.library.tracks.firstOrNull { it.id == inputs.playback.currentTrackId },
            download = inputs.download,
        )
    }.combine(downloadUrl) { state, url ->
        state.copy(downloadUrl = url)
    }.combine(downloadTitle) { state, title ->
        state.copy(downloadTitle = title)
    }.combine(linkDownloader.yandexAuthState) { state, yandexAuth ->
        state.copy(yandexAuth = yandexAuth)
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

    fun updateDownloadUrl(url: String) {
        downloadUrl.value = url
    }

    fun updateDownloadTitle(title: String) {
        downloadTitle.value = title
    }

    fun openSharedLink(sharedText: String?) {
        val normalized = DownloadParsing.normalizeUserInput(sharedText.orEmpty())
        if (!DownloadParsing.isDownloadableUrl(normalized)) return

        downloadUrl.value = normalized
        downloadTitle.value = ""
        selectedTab.value = LuxTab.DOWNLOAD
    }

    fun restorePlayback() = playbackGateway.restorePlayback()

    fun reportActivityFailure(action: () -> Unit) {
        messagesFlow.attempt("Не удалось открыть системное окно выбора. Проверьте доступные приложения.", action)
    }

    fun importAudio(uris: List<Uri>, playlistName: String? = null) {
        if (uris.isEmpty()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val imported = runCatchingCancellable { libraryStore.importUris(uris) }.getOrElse { error ->
                messagesFlow.report(error, "Не удалось импортировать выбранные файлы.")
                return@launch
            }
            val normalizedPlaylistName = playlistName?.trim().orEmpty()
            var playlistCreated = false
            if (imported.isNotEmpty()) {
                if (normalizedPlaylistName.isNotEmpty()) {
                    playlistCreated = createPlaylistForImportedTracks(
                        playlistName = normalizedPlaylistName,
                        tracks = imported,
                    )
                    selectedTab.value = if (playlistCreated) LuxTab.PLAYLISTS else LuxTab.LIBRARY
                } else {
                    selectedTab.value = LuxTab.LIBRARY
                }
            }
            messagesFlow.emit(
                if (imported.isEmpty()) {
                    "Не удалось импортировать выбранные файлы."
                } else if (normalizedPlaylistName.isNotEmpty() && !playlistCreated) {
                    "Треки добавлены в библиотеку, но плейлист создать не удалось. Данные музыки сохранены."
                } else if (normalizedPlaylistName.isNotEmpty()) {
                    "Добавлено ${imported.size} трек(ов) и создан плейлист «$normalizedPlaylistName»."
                } else {
                    "Добавлено ${imported.size} трек(ов) в локальную библиотеку."
                },
            )
        }
    }

    fun createPlaylist(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            libraryStore.createPlaylist(normalized)
            selectedTab.value = LuxTab.PLAYLISTS
            messagesFlow.emit("Плейлист \"$normalized\" создан.")
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
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

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            libraryStore.removeTrackFromPlaylist(playlistId, trackId)
            messagesFlow.emit("Трек удалён из плейлиста.")
        }
    }

    fun updatePlaylistName(playlistId: String, name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val previousName = libraryStore.snapshot.value.playlists
                .firstOrNull { it.id == playlistId }
                ?.name
            val updated = libraryStore.updatePlaylistName(playlistId, normalized)
            if (updated != null && previousName != null) {
                playbackGateway.updateQueueTitle(previousName, updated.name)
                messagesFlow.emit("Название плейлиста обновлено.")
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val removed = libraryStore.deletePlaylist(playlistId)
            if (removed != null) {
                playbackGateway.clearActivePlaylist(playlistId)
            }
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
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            playbackGateway.removeTrack(trackId)
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

    fun updateTrackDetails(trackId: String, title: String, artist: String) {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        if (normalizedTitle.isBlank() || normalizedArtist.isBlank()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            libraryStore.updateTrackDetails(trackId, normalizedTitle, normalizedArtist)?.let { updated ->
                playbackGateway.updateTrack(updated.id)
            }
        }
    }

    fun updateTrackArtwork(trackId: String, uri: Uri) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val updated = libraryStore.updateTrackArtwork(trackId, uri)
            if (updated != null) {
                playbackGateway.updateTrack(updated.id)
                messagesFlow.emit("Обложка трека обновлена.")
            } else {
                messagesFlow.emit("Не удалось загрузить изображение.")
            }
        }
    }

    fun updatePlaylistArtwork(playlistId: String, uri: Uri) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val updated = libraryStore.updatePlaylistArtwork(playlistId, uri)
            messagesFlow.emit(
                if (updated != null) "Обложка плейлиста обновлена."
                else "Не удалось загрузить изображение.",
            )
        }
    }

    fun updateArtistArtwork(artist: String, uri: Uri) {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val updated = libraryStore.updateArtistArtwork(artist, uri)
            messagesFlow.emit(
                if (updated != null) "Изображение артиста обновлено."
                else "Не удалось загрузить изображение.",
            )
        }
    }

    fun toggleLibraryTrack(trackId: String) {
        val queue = uiState.value.visibleTracks.ifEmpty { uiState.value.library }
        val index = queue.indexOfFirst { it.id == trackId }
        if (index >= 0) {
            playbackGateway.playCollection(queue, index, "Библиотека")
        }
    }

    fun playTrack(trackId: String) = toggleLibraryTrack(trackId)

    fun playPlaylist(playlistId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        if (queue.isNotEmpty()) {
            playbackGateway.playCollection(queue, 0, playlist.name, playlist.id)
        }
    }

    fun playPlaylistTrack(playlistId: String, trackId: String) {
        val playlist = uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val tracksById = uiState.value.library.associateBy { it.id }
        val queue = playlist.trackIds.mapNotNull(tracksById::get)
        val startIndex = queue.indexOfFirst { it.id == trackId }
        if (startIndex >= 0) {
            playbackGateway.playCollection(queue, startIndex, playlist.name, playlist.id)
        }
    }

    fun playArtistTrack(artist: String, trackId: String) {
        val queue = uiState.value.library.filter { it.artist.equals(artist, ignoreCase = true) }
        val startIndex = queue.indexOfFirst { it.id == trackId }
        if (startIndex >= 0) {
            playbackGateway.playCollection(queue, startIndex, "Артист • $artist")
        }
    }

    fun togglePlayback() = playbackGateway.togglePlayback()

    fun skipNext() = playbackGateway.skipNext()

    fun skipPrevious() = playbackGateway.skipPrevious()

    fun toggleShuffle() = playbackGateway.toggleShuffle()

    fun cycleRepeat() = playbackGateway.cycleRepeatMode()

    fun seekToFraction(fraction: Float) = playbackGateway.seekToFraction(fraction)

    fun selectQueueTrack(trackId: String) = playbackGateway.selectQueueTrack(trackId)

    fun connectYandexMusic() {
        viewModelScope.launch(messagesFlow.exceptionHandler) {
            linkDownloader.beginYandexAuthorization().onSuccess { code ->
                authBrowserRequestsFlow.send(
                    YandexAuthBrowserRequest(
                        url = code.verificationUrl,
                        userCode = code.userCode,
                    ),
                )
                YandexAuthorizationService.start(getApplication()).onFailure { error ->
                    messagesFlow.emit(
                        error.message ?: "Не удалось запустить фоновое ожидание авторизации.",
                    )
                }
            }.onFailure { error ->
                messagesFlow.report(error, "Не удалось подключить Яндекс Музыку.")
            }
        }
    }

    fun resumeYandexMusicAuthorization() {
        if (!linkDownloader.hasPendingYandexAuthorization()) return
        YandexAuthorizationService.start(getApplication()).onFailure { error ->
            viewModelScope.launch(messagesFlow.exceptionHandler) {
                messagesFlow.emit(error.message ?: "Не удалось продолжить авторизацию Яндекс Музыки.")
            }
        }
    }

    fun disconnectYandexMusic() {
        messagesFlow.attempt("Не удалось отключить Яндекс Музыку.") {
            YandexAuthorizationService.stop(getApplication())
            linkDownloader.disconnectYandex()
            messagesFlow.emit("Аккаунт Яндекс Музыки отключён.")
        }
    }

    fun downloadFromLink(url: String, title: String, playlistName: String? = null) {
        val normalized = url.trim()
        val customTitle = title.trim()
        val normalizedPlaylistName = playlistName?.trim().orEmpty()
        if (normalized.isBlank()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val result = linkDownloader.downloadCollection(normalized).getOrElse { error ->
                messagesFlow.emit(linkDownloader.state.value.errorMessage ?: com.luxmusic.android.download.DownloadFailureText.from(error, "Не удалось скачать музыку по ссылке."))
                return@launch
            }
            val imported = result.tracks
            if (customTitle.isNotBlank()) {
                imported.takeIf { it.size == 1 }?.forEach { track ->
                    runCatchingCancellable {
                        libraryStore.updateTrackDetails(track.id, customTitle, track.artist)
                    }.onFailure { messagesFlow.report(it, "Трек сохранён, но название изменить не удалось.") }
                }
            }
            val playlistsCreated = createPlaylistsForDownload(
                result = result,
                customPlaylistName = normalizedPlaylistName,
            )
            val playlistCreated = playlistsCreated > 0

            downloadUrl.value = ""
            downloadTitle.value = ""
            selectedTab.value = if (playlistCreated) LuxTab.PLAYLISTS else LuxTab.LIBRARY
            val completionMessage = when {
                    playlistsCreated > 1 ->
                        "Скачано ${imported.size} трек(ов) и создано $playlistsCreated плейлист(ов) по альбомам."
                    playlistCreated ->
                        "Скачано ${imported.size} трек(ов) и создан плейлист."
                    normalizedPlaylistName.isNotEmpty() ->
                        "Музыка скачана, но плейлист создать не удалось. Треки сохранены в библиотеке."
                    else -> "Скачано и сохранено ${imported.size} трек(ов)."
                }
            messagesFlow.emit(
                if (result.warnings.isEmpty()) {
                    completionMessage
                } else {
                    "$completionMessage Не удалось скачать: ${result.warnings.size}."
                },
            )
        }
    }

    fun downloadArchiveFromLink(url: String, playlistName: String? = null) {
        val normalized = url.trim()
        val normalizedPlaylistName = playlistName?.trim().orEmpty()
        if (normalized.isBlank()) return

        viewModelScope.launch(messagesFlow.exceptionHandler) {
            val imported = linkDownloader.downloadArchive(normalized).getOrElse { error ->
                messagesFlow.report(error, "Не удалось скачать ZIP-архив.")
                return@launch
            }
            val playlistCreated = normalizedPlaylistName.isNotEmpty() &&
                createPlaylistForImportedTracks(normalizedPlaylistName, imported)

            downloadUrl.value = ""
            downloadTitle.value = ""
            selectedTab.value = if (playlistCreated) LuxTab.PLAYLISTS else LuxTab.LIBRARY
            messagesFlow.emit(
                when {
                    playlistCreated ->
                        "Из архива добавлено ${imported.size} трек(ов) и создан плейлист «$normalizedPlaylistName»."
                    normalizedPlaylistName.isNotEmpty() ->
                        "Архив импортирован, но плейлист создать не удалось. Треки сохранены в библиотеке."
                    else -> "Из ZIP-архива добавлено ${imported.size} трек(ов)."
                },
            )
        }
    }

    private suspend fun createPlaylistForImportedTracks(
        playlistName: String,
        tracks: List<Track>,
    ): Boolean {
        return runCatchingCancellable {
            libraryStore.createPlaylist(
                name = playlistName,
                trackIds = tracks.map(Track::id),
            )
        }.onFailure { messagesFlow.report(it, "Не удалось создать плейлист. Треки сохранены в библиотеке.") }.isSuccess
    }

    private suspend fun createPlaylistsForDownload(
        result: DownloadCollectionResult,
        customPlaylistName: String,
    ): Int {
        val drafts = buildList {
            if (result.sourceKind == YandexSourceKind.ARTIST) {
                result.playlistGroups.forEach { group ->
                    val prefix = result.collectionLabel?.takeIf(String::isNotBlank)
                    add(
                        PlaylistDraft(
                            name = listOfNotNull(prefix, group.name).joinToString(" — "),
                            trackIds = group.trackIds,
                        ),
                    )
                }
            } else if (result.sourceKind == YandexSourceKind.ALBUM && customPlaylistName.isBlank()) {
                result.playlistGroups.firstOrNull()?.let { group ->
                    add(PlaylistDraft(group.name, group.trackIds))
                }
            }
            if (customPlaylistName.isNotBlank()) {
                add(PlaylistDraft(customPlaylistName, result.tracks.map(Track::id)))
            }
        }
        if (drafts.isEmpty()) return 0
        return runCatchingCancellable { libraryStore.createPlaylists(drafts) }
            .onFailure { messagesFlow.report(it, "Не удалось создать плейлисты. Треки сохранены в библиотеке.") }
            .getOrDefault(emptyList()).size
    }

    private data class CombinedUiInputs(
        val library: com.luxmusic.android.data.LibrarySnapshot,
        val playback: PlaybackState,
        val download: DownloadState,
        val query: String,
    )
}
