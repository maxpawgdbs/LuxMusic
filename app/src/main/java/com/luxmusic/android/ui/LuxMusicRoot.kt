package com.luxmusic.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.LuxTab
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxMusicRoot(
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
    onImportDownloadAccount: (DownloadService) -> Unit,
    onClearDownloadAccount: (DownloadService) -> Unit,
) {
    val tracksById = remember(uiState.library) { uiState.library.associateBy { it.id } }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var createPlaylistName by rememberSaveable { mutableStateOf("") }
    var downloadUrl by rememberSaveable { mutableStateOf("") }
    var openedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var lyricsTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTargetTrack by remember { mutableStateOf<Track?>(null) }
    var deleteTargetTrack by remember { mutableStateOf<Track?>(null) }
    var deleteTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val openedPlaylist = remember(uiState.playlists, openedPlaylistId) {
        uiState.playlists.firstOrNull { it.id == openedPlaylistId }
    }
    val playlistEditor = remember(uiState.playlists, playlistEditorId) {
        uiState.playlists.firstOrNull { it.id == playlistEditorId }
    }

    LaunchedEffect(openedPlaylistId, uiState.playlists) {
        if (openedPlaylistId != null && openedPlaylist == null) {
            openedPlaylistId = null
        }
    }
    LaunchedEffect(playlistEditorId, uiState.playlists) {
        if (playlistEditorId != null && playlistEditor == null) {
            playlistEditorId = null
        }
    }

    BackHandler(enabled = uiState.selectedTab == LuxTab.PLAYLISTS && openedPlaylist != null) {
        openedPlaylistId = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(openedPlaylist?.name ?: uiState.selectedTab.title()) },
                navigationIcon = {
                    if (uiState.selectedTab == LuxTab.PLAYLISTS && openedPlaylist != null) {
                        IconButton(onClick = { openedPlaylistId = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (uiState.selectedTab == LuxTab.HOME || uiState.selectedTab == LuxTab.LIBRARY) {
                        IconButton(onClick = onImportClick) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = "Импортировать")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                LuxTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = {
                            if (tab != LuxTab.PLAYLISTS) {
                                openedPlaylistId = null
                            }
                            onSelectTab(tab)
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    LuxTab.HOME -> Icons.Rounded.Home
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
        when (uiState.selectedTab) {
            LuxTab.HOME -> {
                LuxHomePage(
                    contentPadding = paddingValues,
                    uiState = uiState,
                    onImportClick = onImportClick,
                    onTogglePlayback = onTogglePlayback,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onSeekToFraction = onSeekToFraction,
                )
            }

            LuxTab.LIBRARY -> {
                LuxLibraryPage(
                    contentPadding = paddingValues,
                    query = uiState.searchQuery,
                    tracks = uiState.visibleTracks,
                    currentTrackId = uiState.currentTrack?.id,
                    isPlaying = uiState.playback.isPlaying,
                    librarySize = uiState.library.size,
                    onQueryChange = onSearchChange,
                    onImportClick = onImportClick,
                    onPlay = onPlayTrack,
                    onShowLyrics = { lyricsTrack = it },
                    onAddToPlaylist = { playlistTargetTrack = it },
                    onDelete = { deleteTargetTrack = it },
                )
            }

            LuxTab.PLAYLISTS -> {
                if (openedPlaylist != null) {
                    LuxPlaylistDetailPage(
                        contentPadding = paddingValues,
                        playlist = openedPlaylist,
                        tracks = openedPlaylist.trackIds.mapNotNull(tracksById::get),
                        onPlayPlaylist = { onPlayPlaylist(openedPlaylist.id) },
                        onPlayTrack = { trackId -> onPlayPlaylistTrack(openedPlaylist.id, trackId) },
                        onAddTracks = { playlistEditorId = openedPlaylist.id },
                        onDeletePlaylist = { deleteTargetPlaylist = openedPlaylist },
                    )
                } else {
                    LuxPlaylistsPage(
                        contentPadding = paddingValues,
                        playlists = uiState.playlists,
                        tracksById = tracksById,
                        onOpenPlaylist = { openedPlaylistId = it },
                        onPlayPlaylist = onPlayPlaylist,
                        onCreatePlaylist = { showCreatePlaylist = true },
                    )
                }
            }

            LuxTab.DOWNLOAD -> {
                LuxDownloadPage(
                    contentPadding = paddingValues,
                    url = downloadUrl,
                    onUrlChange = { downloadUrl = it },
                    onDownload = { onDownloadLink(downloadUrl) },
                    uiState = uiState,
                    onImportDownloadAccount = onImportDownloadAccount,
                    onClearDownloadAccount = onClearDownloadAccount,
                )
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
                    onValueChange = { createPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(createPlaylistName)
                        createPlaylistName = ""
                        showCreatePlaylist = false
                    },
                    enabled = createPlaylistName.isNotBlank(),
                    colors = luxPrimaryButtonColors(),
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
                    text = lyricsTrack?.lyrics ?: "У этого трека пока нет сохраненного текста.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
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

    if (playlistEditor != null) {
        val availableTracks = uiState.library.filterNot { track -> track.id in playlistEditor.trackIds }
        AlertDialog(
            onDismissRequest = { playlistEditorId = null },
            title = { Text("Добавить треки") },
            text = {
                if (availableTracks.isEmpty()) {
                    Text("В библиотеке нет новых треков для плейлиста \"${playlistEditor.name}\".")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        availableTracks.forEach { track ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddTrackToPlaylist(playlistEditor.id, track.id) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${track.artist} • ${track.album}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        formatDuration(track.durationMs),
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
                TextButton(onClick = { playlistEditorId = null }) {
                    Text("Готово")
                }
            },
        )
    }

    if (deleteTargetTrack != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetTrack = null },
            title = { Text("Удалить трек") },
            text = {
                Text("Трек \"${deleteTargetTrack?.title}\" будет удален с устройства и из всех плейлистов.")
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

    if (deleteTargetPlaylist != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetPlaylist = null },
            title = { Text("Удалить плейлист") },
            text = {
                Text("Плейлист \"${deleteTargetPlaylist?.name}\" будет удален. Треки в библиотеке останутся.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetPlaylist?.let {
                            onDeletePlaylist(it.id)
                            if (openedPlaylistId == it.id) {
                                openedPlaylistId = null
                            }
                        }
                        deleteTargetPlaylist = null
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetPlaylist = null }) {
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
