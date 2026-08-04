package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track

@Composable
internal fun LuxLibraryPage(
    contentPadding: PaddingValues,
    query: String,
    tracks: List<Track>,
    currentTrackId: String?,
    librarySize: Int,
    playlistCount: Int,
    onQueryChange: (String) -> Unit,
    onPlay: (String) -> Unit,
    onShowLyrics: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onDelete: (Track) -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = luxCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Коллекция", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LuxStatChip(Icons.Rounded.LibraryMusic, librarySize.toString(), "Треков")
                        LuxStatChip(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            playlistCount.toString(),
                            "Плейлистов",
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text("Поиск") },
                singleLine = true,
            )
        }

        if (tracks.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Библиотека пуста",
                    body = "Импортируйте музыку в разделе «Загрузка».",
                )
            }
        } else {
            items(tracks, key = Track::id) { track ->
                val isCurrent = currentTrackId == track.id
                var menuExpanded by remember(track.id) { mutableStateOf(false) }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    onClick = { onPlay(track.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(track.artworkPath, modifier = Modifier.size(68.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LuxMarqueeText(
                                text = track.title,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${track.artist} • ${track.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatDuration(track.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Действия")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Изменить") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit(track)
                                    },
                                )
                                if (!track.lyrics.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Текст песни") },
                                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onShowLyrics(track)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Добавить в плейлист") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist(track)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete(track)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxPlaylistsPage(
    contentPadding: PaddingValues,
    playlists: List<Playlist>,
    tracksById: Map<String, Track>,
    activePlaylistId: String?,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (playlists.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Нет плейлистов",
                    body = "Создайте первый плейлист.",
                )
            }
        } else {
            items(playlists, key = Playlist::id) { playlist ->
                val isActive = playlist.id == activePlaylistId
                val tracks = remember(playlist.trackIds, tracksById) {
                    playlist.trackIds.mapNotNull(tracksById::get)
                }
                val artworkPath = tracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    onClick = { onOpenPlaylist(playlist.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(artworkPath, modifier = Modifier.size(92.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LuxMarqueeText(
                                text = playlist.name,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${tracks.size} трек(ов)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledIconButton(
                            onClick = { onPlayPlaylist(playlist.id) },
                            enabled = tracks.isNotEmpty(),
                            colors = luxFilledIconButtonColors(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCreatePlaylist,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                colors = luxPrimaryButtonColors(),
            ) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать плейлист")
            }
        }
    }
}

@Composable
internal fun LuxPlaylistDetailPage(
    contentPadding: PaddingValues,
    playlist: Playlist,
    tracks: List<Track>,
    currentTrackId: String?,
    onPlayPlaylist: () -> Unit,
    onPlayTrack: (String) -> Unit,
    onShowLyrics: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onAddTracks: () -> Unit,
    onDeletePlaylist: () -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = luxCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(
                            tracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath,
                            modifier = Modifier.size(104.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LuxMarqueeText(
                                text = playlist.name,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = "${tracks.size} трек(ов)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledIconButton(
                            onClick = onPlayPlaylist,
                            enabled = tracks.isNotEmpty(),
                            colors = luxFilledIconButtonColors(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть")
                        }
                        OutlinedIconButton(onClick = onAddTracks) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "Добавить треки",
                            )
                        }
                        OutlinedIconButton(onClick = onDeletePlaylist) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить плейлист",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Плейлист пуст",
                    body = "Добавьте треки из библиотеки.",
                )
            }
        } else {
            items(tracks, key = Track::id) { track ->
                val isCurrent = track.id == currentTrackId
                var menuExpanded by remember(track.id) { mutableStateOf(false) }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    onClick = { onPlayTrack(track.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(track.artworkPath, modifier = Modifier.size(64.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LuxMarqueeText(
                                text = track.title,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Действия")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Изменить") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit(track)
                                    },
                                )
                                if (!track.lyrics.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Текст песни") },
                                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onShowLyrics(track)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Добавить в плейлист") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onAddToPlaylist(track)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить из плейлиста") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveTrack(track.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить с устройства") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteTrack(track)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxArtistsPage(
    contentPadding: PaddingValues,
    tracks: List<Track>,
    onOpenArtist: (String) -> Unit,
) {
    val artists = remember(tracks) {
        tracks
            .groupBy { it.artist.trim().ifBlank { "Неизвестный артист" } }
            .toList()
            .sortedBy { (artist, _) -> artist.lowercase() }
    }

    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artists.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Нет артистов",
                    body = "Артисты появятся после добавления музыки.",
                )
            }
        } else {
            items(artists, key = { it.first }) { (artist, artistTracks) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = luxCardColors(),
                    onClick = { onOpenArtist(artist) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(
                            artistTracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath,
                            modifier = Modifier.size(82.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LuxMarqueeText(
                                text = artist,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "${artistTracks.size} трек(ов)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxArtistDetailPage(
    contentPadding: PaddingValues,
    artist: String,
    tracks: List<Track>,
    currentTrackId: String?,
    onPlayTrack: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = luxCardColors(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkThumb(
                        tracks.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath,
                        modifier = Modifier.size(108.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LuxMarqueeText(
                            text = artist,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "${tracks.size} трек(ов)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(tracks, key = Track::id) { track ->
            val isCurrent = track.id == currentTrackId
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                onClick = { onPlayTrack(track.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkThumb(track.artworkPath, modifier = Modifier.size(64.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LuxMarqueeText(
                            text = track.title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = track.album,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatDuration(track.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LuxQueuePage(
    contentPadding: PaddingValues,
    tracks: List<Track>,
    currentTrackId: String?,
    onSelectTrack: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (tracks.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Очередь пуста",
                    body = "Запустите трек или плейлист.",
                )
            }
        } else {
            items(tracks, key = Track::id) { track ->
                val isCurrent = track.id == currentTrackId
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    onClick = { onSelectTrack(track.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(track.artworkPath, modifier = Modifier.size(72.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LuxMarqueeText(
                                text = track.title,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxDownloadPage(
    contentPadding: PaddingValues,
    url: String,
    onUrlChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onDownload: () -> Unit,
    uiState: LuxMusicUiState,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            FilledTonalButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                colors = luxTonalButtonColors(),
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Импортировать из файла")
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = luxCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Скачать по ссылке", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ссылка") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        enabled = !uiState.download.isRunning,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название песни (необязательно)") },
                        enabled = !uiState.download.isRunning,
                        singleLine = true,
                    )
                    Button(
                        onClick = onDownload,
                        enabled = uiState.download.isAvailable && !uiState.download.isRunning && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        colors = luxPrimaryButtonColors(),
                    ) {
                        Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.download.isRunning) "Загрузка..." else "Скачать")
                    }
                    if (uiState.download.isRunning) {
                        LinearProgressIndicator(
                            progress = { uiState.download.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = uiState.download.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.download.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
