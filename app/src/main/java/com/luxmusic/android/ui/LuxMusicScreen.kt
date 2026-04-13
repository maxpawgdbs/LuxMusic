package com.luxmusic.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.LuxTab
import com.luxmusic.android.data.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxMusicScreen(
    uiState: LuxMusicUiState,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (LuxTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayPlaylistTrack: (String, String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onDownloadLink: (String) -> Unit,
) {
    LuxMusicRoot(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSelectTab = onSelectTab,
        onSearchChange = onSearchChange,
        onImportClick = onImportClick,
        onCreatePlaylist = onCreatePlaylist,
        onAddTrackToPlaylist = onAddTrackToPlaylist,
        onDeleteTrack = onDeleteTrack,
        onDeletePlaylist = onDeletePlaylist,
        onPlayTrack = onPlayTrack,
        onPlayPlaylist = onPlayPlaylist,
        onPlayPlaylistTrack = onPlayPlaylistTrack,
        onTogglePlayback = onTogglePlayback,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        onSeekToFraction = onSeekToFraction,
        onDownloadLink = onDownloadLink,
    )
    return

    val tracksById = remember(uiState.library) { uiState.library.associateBy { it.id } }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var createPlaylistName by rememberSaveable { mutableStateOf("") }
    var lyricsTrack by remember(uiState.library) { mutableStateOf<Track?>(null) }
    var playlistTargetTrack by remember(uiState.library) { mutableStateOf<Track?>(null) }
    var deleteTargetTrack by remember(uiState.library) { mutableStateOf<Track?>(null) }
    var downloadUrl by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DecorativeBackground()

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("LuxMusic") },
                    actions = {
                        IconButton(onClick = onImportClick) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    LuxTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = uiState.selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        LuxTab.HOME -> Icons.Rounded.LibraryMusic
                                        LuxTab.LIBRARY -> Icons.Rounded.LibraryMusic
                                        LuxTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                                        LuxTab.DOWNLOAD -> Icons.Rounded.DownloadForOffline
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tab.title()) },
                        )
                    }
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    LuxOverviewSection(
                        uiState = uiState,
                        currentTrack = uiState.currentTrack,
                        onImportClick = onImportClick,
                        onTogglePlayback = onTogglePlayback,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onSeekToFraction = onSeekToFraction,
                    )
                }

                when (uiState.selectedTab) {
                    LuxTab.HOME -> Unit
                    LuxTab.LIBRARY -> {
                        item {
                            LuxLibraryToolbarCard(
                                query = uiState.searchQuery,
                                librarySize = uiState.library.size,
                                onQueryChange = onSearchChange,
                                onImportClick = onImportClick,
                            )
                        }

                        if (uiState.visibleTracks.isEmpty()) {
                            item {
                                LuxEmptyStateCard(
                                    title = "Библиотека пока пустая",
                                    body = "Импортируйте локальные файлы или воспользуйтесь загрузкой по ссылке, чтобы собрать офлайн-коллекцию.",
                                )
                            }
                        } else {
                            items(uiState.visibleTracks, key = { it.id }) { track ->
                                LuxTrackCard(
                                    track = track,
                                    isCurrent = uiState.currentTrack?.id == track.id,
                                    onPlay = { onPlayTrack(track.id) },
                                    onShowLyrics = { lyricsTrack = track },
                                    onAddToPlaylist = { playlistTargetTrack = track },
                                    onDelete = { deleteTargetTrack = track },
                                )
                            }
                        }
                    }

                    LuxTab.PLAYLISTS -> {
                        item {
                            LuxPlaylistHeaderCard(
                                playlistsCount = uiState.playlists.size,
                                onCreatePlaylist = { showCreatePlaylist = true },
                            )
                        }

                        if (uiState.playlists.isEmpty()) {
                            item {
                                LuxEmptyStateCard(
                                    title = "Плейлистов пока нет",
                                    body = "Создайте подборку и раскладывайте локальные треки по настроению, жанру или поездке.",
                                )
                            }
                        } else {
                            items(uiState.playlists, key = { it.id }) { playlist ->
                                LuxPlaylistCard(
                                    playlist = playlist,
                                    tracksById = tracksById,
                                    onPlayPlaylist = { onPlayPlaylist(playlist.id) },
                                )
                            }
                        }
                    }

                    LuxTab.DOWNLOAD -> {
                        item {
                            LuxDownloadCard(
                                url = downloadUrl,
                                onUrlChange = { value -> downloadUrl = value },
                                onDownload = { onDownloadLink(downloadUrl) },
                                uiState = uiState,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = createPlaylistName,
                    onValueChange = { value -> createPlaylistName = value },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreatePlaylist(createPlaylistName)
                        createPlaylistName = ""
                        showCreatePlaylist = false
                    },
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (lyricsTrack != null) {
        AlertDialog(
            onDismissRequest = { lyricsTrack = null },
            title = { Text(lyricsTrack?.title ?: "Текст") },
            text = {
                Text(
                    text = lyricsTrack?.lyrics ?: "У этого трека пока нет сохранённого текста.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { lyricsTrack = null }) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (playlistTargetTrack != null) {
        AlertDialog(
            onDismissRequest = { playlistTargetTrack = null },
            title = { Text("Добавить в плейлист") },
            text = {
                if (uiState.playlists.isEmpty()) {
                    Text("Сначала создайте хотя бы один плейлист.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.playlists.forEach { playlist ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playlistTargetTrack?.let { track ->
                                            onAddTrackToPlaylist(playlist.id, track.id)
                                        }
                                        playlistTargetTrack = null
                                    },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${playlist.trackIds.size} трек(ов)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistTargetTrack = null }) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (deleteTargetTrack != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetTrack = null },
            title = { Text("Удалить трек") },
            text = {
                Text("Трек \"${deleteTargetTrack?.title}\" будет удалён с устройства и из всех плейлистов.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetTrack?.let { onDeleteTrack(it.id) }
                        deleteTargetTrack = null
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetTrack = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

private fun LuxTab.title(): String = when (this) {
    LuxTab.HOME -> "Главная"
    LuxTab.LIBRARY -> "Библиотека"
    LuxTab.PLAYLISTS -> "Плейлисты"
    LuxTab.DOWNLOAD -> "Загрузка"
}
