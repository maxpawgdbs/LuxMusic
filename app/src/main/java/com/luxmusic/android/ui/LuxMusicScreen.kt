package com.luxmusic.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.LuxTab
import com.luxmusic.android.data.Track

@Composable
fun LuxMusicScreen(
    uiState: LuxMusicUiState,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (LuxTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onDownloadLink: (String) -> Unit,
) {
    val tracksById = remember(uiState.library) { uiState.library.associateBy { it.id } }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var createPlaylistName by rememberSaveable { mutableStateOf("") }
    var lyricsTrack by remember(uiState.library) { mutableStateOf<Track?>(null) }
    var playlistTargetTrack by remember(uiState.library) { mutableStateOf<Track?>(null) }
    var downloadUrl by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface),
                ),
            ),
    ) {
        DecorativeBackground()

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    HeaderBlock(onImportClick = onImportClick)
                }

                item {
                    HeroPlayerCard(
                        currentTrack = uiState.currentTrack,
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LuxTab.entries.forEach { tab ->
                            FilterChip(
                                selected = uiState.selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                label = { Text(tab.title()) },
                                leadingIcon = {
                                    androidx.compose.material3.Icon(
                                        imageVector = when (tab) {
                                            LuxTab.LIBRARY -> Icons.Rounded.LibraryMusic
                                            LuxTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
                                            LuxTab.DOWNLOAD -> Icons.Rounded.Download
                                        },
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }

                when (uiState.selectedTab) {
                    LuxTab.LIBRARY -> {
                        item {
                            LibraryActions(
                                query = uiState.searchQuery,
                                onQueryChange = onSearchChange,
                                onImportClick = onImportClick,
                            )
                        }

                        if (uiState.visibleTracks.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "Офлайн-библиотека ждёт первую музыку",
                                    body = "Импортируйте локальные файлы или скачайте музыку по ссылке, и LuxMusic сохранит её внутри приложения.",
                                )
                            }
                        } else {
                            items(uiState.visibleTracks, key = { it.id }) { track ->
                                TrackCard(
                                    track = track,
                                    isCurrent = uiState.currentTrack?.id == track.id,
                                    onPlay = { onPlayTrack(track.id) },
                                    onShowLyrics = { lyricsTrack = track },
                                    onAddToPlaylist = { playlistTargetTrack = track },
                                )
                            }
                        }
                    }

                    LuxTab.PLAYLISTS -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Плейлисты", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "Собирайте настроение, подборки и любимые очереди.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FilledTonalButton(onClick = { showCreatePlaylist = true }) {
                                    androidx.compose.material3.Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создать")
                                }
                            }
                        }

                        if (uiState.playlists.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "Плейлистов пока нет",
                                    body = "Создайте первый плейлист и начинайте раскладывать треки по настроению.",
                                )
                            }
                        } else {
                            items(uiState.playlists, key = { it.id }) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    tracksById = tracksById,
                                    onPlayPlaylist = { onPlayPlaylist(playlist.id) },
                                )
                            }
                        }
                    }

                    LuxTab.DOWNLOAD -> {
                        item {
                            DownloadCard(
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
                    onValueChange = { value: String -> createPlaylistName = value },
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
                        .height(240.dp)
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
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
}

@Composable
private fun HeaderBlock(onImportClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("LuxMusic", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Android-плеер, который хранит музыку локально, показывает обложки, подтягивает текст и собирает всё в офлайн-библиотеку.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onImportClick) {
            androidx.compose.material3.Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Импорт")
        }
    }
}

private fun LuxTab.title(): String = when (this) {
    LuxTab.LIBRARY -> "Библиотека"
    LuxTab.PLAYLISTS -> "Плейлисты"
    LuxTab.DOWNLOAD -> "Загрузка"
}
