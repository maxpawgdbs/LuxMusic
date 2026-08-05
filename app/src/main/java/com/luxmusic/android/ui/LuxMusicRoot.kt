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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
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
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxMusicRoot(
    uiState: LuxMusicUiState,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (LuxTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportClick: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onUpdatePlaylistName: (String, String) -> Unit,
    onUpdateTrackDetails: (String, String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayPlaylistTrack: (String, String) -> Unit,
    onPlayArtistTrack: (String, String) -> Unit,
    onSelectQueueTrack: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onDownloadUrlChange: (String) -> Unit,
    onDownloadTitleChange: (String) -> Unit,
    onDownloadLink: (String, String, String?) -> Unit,
) {
    val tracksById = remember(uiState.library) { uiState.library.associateBy { it.id } }
    val queueTracks = remember(uiState.library, uiState.playback.queueTrackIds) {
        uiState.playback.queueTrackIds.mapNotNull(tracksById::get)
    }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var importPlaylistName by rememberSaveable { mutableStateOf("") }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var createPlaylistName by rememberSaveable { mutableStateOf("") }
    var openedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedArtistName by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var lyricsTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTargetTrack by remember { mutableStateOf<Track?>(null) }
    var editTargetTrack by remember { mutableStateOf<Track?>(null) }
    var editTitle by rememberSaveable { mutableStateOf("") }
    var editArtist by rememberSaveable { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var deleteTargetTrack by remember { mutableStateOf<Track?>(null) }
    var deleteTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val openedPlaylist = remember(uiState.playlists, openedPlaylistId) {
        uiState.playlists.firstOrNull { it.id == openedPlaylistId }
    }
    val playlistEditor = remember(uiState.playlists, playlistEditorId) {
        uiState.playlists.firstOrNull { it.id == playlistEditorId }
    }
    val openedArtistTracks = remember(uiState.library, openedArtistName) {
        val artist = openedArtistName
        if (artist == null) emptyList() else uiState.library.filter { it.artist == artist }
    }

    LaunchedEffect(openedPlaylistId, uiState.playlists) {
        if (openedPlaylistId != null && openedPlaylist == null) openedPlaylistId = null
    }
    LaunchedEffect(playlistEditorId, uiState.playlists) {
        if (playlistEditorId != null && playlistEditor == null) playlistEditorId = null
    }

    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(
        enabled = !showQueue && uiState.selectedTab == LuxTab.PLAYLISTS && openedPlaylist != null,
    ) {
        openedPlaylistId = null
    }
    BackHandler(
        enabled = !showQueue && uiState.selectedTab == LuxTab.ARTISTS && openedArtistName != null,
    ) {
        openedArtistName = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val title = when {
                        showQueue -> "Очередь"
                        openedPlaylist != null -> openedPlaylist.name
                        openedArtistName != null -> openedArtistName.orEmpty()
                        uiState.selectedTab == LuxTab.HOME -> ""
                        else -> uiState.selectedTab.title()
                    }
                    if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    if (
                        showQueue ||
                        (uiState.selectedTab == LuxTab.PLAYLISTS && openedPlaylist != null) ||
                        (uiState.selectedTab == LuxTab.ARTISTS && openedArtistName != null)
                    ) {
                        IconButton(
                            onClick = {
                                when {
                                    showQueue -> showQueue = false
                                    openedPlaylist != null -> openedPlaylistId = null
                                    else -> openedArtistName = null
                                }
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (!showQueue && uiState.selectedTab == LuxTab.HOME) {
                        IconButton(
                            onClick = { showQueue = true },
                            enabled = uiState.playback.queueTrackIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Открыть очередь",
                            )
                        }
                    } else if (!showQueue && openedPlaylist != null) {
                        IconButton(
                            onClick = {
                                playlistToRename = openedPlaylist
                                playlistName = openedPlaylist.name
                            },
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Переименовать")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showQueue) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    LuxTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = uiState.selectedTab == tab,
                            onClick = {
                                openedPlaylistId = null
                                openedArtistName = null
                                onSelectTab(tab)
                            },
                            label = { Text(tab.title(), style = MaterialTheme.typography.labelMedium) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        LuxTab.HOME -> Icons.Rounded.Home
                                        LuxTab.LIBRARY -> Icons.Rounded.LibraryMusic
                                        LuxTab.ARTISTS -> Icons.Rounded.Groups
                                        LuxTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                                        LuxTab.DOWNLOAD -> Icons.Rounded.DownloadForOffline
                                    },
                                    contentDescription = tab.title(),
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->

        if (showQueue) {
            LuxQueuePage(
                contentPadding = paddingValues,
                tracks = queueTracks,
                currentTrackId = uiState.currentTrack?.id,
                onSelectTrack = onSelectQueueTrack,
            )
        } else {
            when (uiState.selectedTab) {
                LuxTab.HOME -> {
                    LuxHomePage(
                        contentPadding = paddingValues,
                        uiState = uiState,
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
                        librarySize = uiState.library.size,
                        playlistCount = uiState.playlists.size,
                        totalDurationMs = uiState.library.sumOf(Track::durationMs),
                        onQueryChange = onSearchChange,
                        onPlay = onPlayTrack,
                        onShowLyrics = { lyricsTrack = it },
                        onAddToPlaylist = { playlistTargetTrack = it },
                        onEdit = { track ->
                            editTargetTrack = track
                            editTitle = track.title
                            editArtist = track.artist
                        },
                        onDelete = { deleteTargetTrack = it },
                    )
                }

                LuxTab.ARTISTS -> {
                    val artist = openedArtistName
                    if (artist == null) {
                        LuxArtistsPage(
                            contentPadding = paddingValues,
                            tracks = uiState.library,
                            currentArtist = uiState.currentTrack?.artist,
                            onOpenArtist = { openedArtistName = it },
                        )
                    } else {
                        LuxArtistDetailPage(
                            contentPadding = paddingValues,
                            artist = artist,
                            tracks = openedArtistTracks,
                            currentTrackId = uiState.currentTrack?.id,
                            onPlayTrack = { trackId -> onPlayArtistTrack(artist, trackId) },
                        )
                    }
                }

                LuxTab.PLAYLISTS -> {
                    if (openedPlaylist != null) {
                        LuxPlaylistDetailPage(
                            contentPadding = paddingValues,
                            playlist = openedPlaylist,
                            tracks = openedPlaylist.trackIds.mapNotNull(tracksById::get),
                            currentTrackId = uiState.currentTrack?.id,
                            onPlayPlaylist = { onPlayPlaylist(openedPlaylist.id) },
                            onPlayTrack = { trackId -> onPlayPlaylistTrack(openedPlaylist.id, trackId) },
                            onShowLyrics = { lyricsTrack = it },
                            onAddToPlaylist = { playlistTargetTrack = it },
                            onEdit = { track ->
                                editTargetTrack = track
                                editTitle = track.title
                                editArtist = track.artist
                            },
                            onRemoveTrack = { trackId ->
                                onRemoveTrackFromPlaylist(openedPlaylist.id, trackId)
                            },
                            onDeleteTrack = { deleteTargetTrack = it },
                            onAddTracks = { playlistEditorId = openedPlaylist.id },
                            onDeletePlaylist = { deleteTargetPlaylist = openedPlaylist },
                        )
                    } else {
                        LuxPlaylistsPage(
                            contentPadding = paddingValues,
                            playlists = uiState.playlists,
                            tracksById = tracksById,
                            activePlaylistId = uiState.playback.activePlaylistId,
                            onOpenPlaylist = { openedPlaylistId = it },
                            onPlayPlaylist = onPlayPlaylist,
                            onCreatePlaylist = { showCreatePlaylist = true },
                        )
                    }
                }

                LuxTab.DOWNLOAD -> {
                    LuxDownloadPage(
                        contentPadding = paddingValues,
                        url = uiState.downloadUrl,
                        onUrlChange = onDownloadUrlChange,
                        title = uiState.downloadTitle,
                        onTitleChange = onDownloadTitleChange,
                        onImportClick = { showImportDialog = true },
                        onDownload = { playlistName ->
                            onDownloadLink(
                                uiState.downloadUrl,
                                uiState.downloadTitle,
                                playlistName,
                            )
                        },
                        uiState = uiState,
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт музыки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Можно выбрать несколько аудиофайлов или один ZIP-архив.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importCreatePlaylist = !importCreatePlaylist },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = importCreatePlaylist,
                            onCheckedChange = { importCreatePlaylist = it },
                        )
                        Text("Создать плейлист из выбранных файлов")
                    }
                    if (importCreatePlaylist) {
                        OutlinedTextField(
                            value = importPlaylistName,
                            onValueChange = { importPlaylistName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Название плейлиста") },
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportClick(
                            importPlaylistName.trim().takeIf { importCreatePlaylist },
                        )
                        showImportDialog = false
                    },
                    enabled = !importCreatePlaylist || importPlaylistName.isNotBlank(),
                    colors = luxPrimaryButtonColors(),
                ) {
                    Text("Выбрать файлы")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Отмена") }
            },
        )
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
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Отмена") }
            },
        )
    }

    playlistToRename?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Название плейлиста") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePlaylistName(playlist.id, playlistName)
                        playlistToRename = null
                    },
                    enabled = playlistName.isNotBlank(),
                    colors = luxPrimaryButtonColors(),
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text("Отмена") }
            },
        )
    }

    editTargetTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { editTargetTrack = null },
            title = { Text("Изменить трек") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Автор") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateTrackDetails(track.id, editTitle, editArtist)
                        editTargetTrack = null
                    },
                    enabled = editTitle.isNotBlank() && editArtist.isNotBlank(),
                    colors = luxPrimaryButtonColors(),
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTargetTrack = null }) { Text("Отмена") }
            },
        )
    }

    lyricsTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { lyricsTrack = null },
            title = { Text(track.title) },
            text = {
                Text(
                    text = track.lyrics ?: "Текст не найден.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { lyricsTrack = null }) { Text("Закрыть") }
            },
        )
    }

    playlistTargetTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { playlistTargetTrack = null },
            title = { Text("Добавить в плейлист") },
            text = {
                if (uiState.playlists.isEmpty()) {
                    Text("Сначала создайте плейлист.")
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
                                        onAddTrackToPlaylist(playlist.id, track.id)
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
                                        playlist.trackIds.size.toString(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistTargetTrack = null }) { Text("Закрыть") }
            },
        )
    }

    playlistEditor?.let { playlist ->
        val availableTracks = uiState.library.filterNot { it.id in playlist.trackIds }
        AlertDialog(
            onDismissRequest = { playlistEditorId = null },
            title = { Text("Добавить треки") },
            text = {
                if (availableTracks.isEmpty()) {
                    Text("Нет доступных треков.")
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
                                    .clickable { onAddTrackToPlaylist(playlist.id, track.id) },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    LuxMarqueeText(
                                        text = track.title,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        track.artist,
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
                TextButton(onClick = { playlistEditorId = null }) { Text("Готово") }
            },
        )
    }

    deleteTargetTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { deleteTargetTrack = null },
            title = { Text("Удалить трек") },
            text = { Text("«${track.title}» будет удалён с устройства.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTrack(track.id)
                        deleteTargetTrack = null
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetTrack = null }) { Text("Отмена") }
            },
        )
    }

    deleteTargetPlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTargetPlaylist = null },
            title = { Text("Удалить плейлист") },
            text = { Text("«${playlist.name}» будет удалён.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(playlist.id)
                        if (openedPlaylistId == playlist.id) openedPlaylistId = null
                        deleteTargetPlaylist = null
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetPlaylist = null }) { Text("Отмена") }
            },
        )
    }
}

private fun LuxTab.title(): String = when (this) {
    LuxTab.HOME -> "Главная"
    LuxTab.LIBRARY -> "Библиотека"
    LuxTab.ARTISTS -> "Артисты"
    LuxTab.PLAYLISTS -> "Плейлисты"
    LuxTab.DOWNLOAD -> "Загрузка"
}
