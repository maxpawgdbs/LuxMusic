package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track

private data class ServiceChipModel(
    val title: String,
    val availableNow: Boolean,
)

private val serviceChips = listOf(
    ServiceChipModel("YouTube", true),
    ServiceChipModel("SoundCloud", true),
    ServiceChipModel("TikTok", true),
    ServiceChipModel("VK", false),
    ServiceChipModel("Яндекс Музыка", false),
    ServiceChipModel("Apple Music", false),
    ServiceChipModel("Spotify", false),
)

@Composable
internal fun LuxLibraryToolbarCard(
    query: String,
    librarySize: Int,
    onQueryChange: (String) -> Unit,
    onImportClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Библиотека", style = MaterialTheme.typography.titleLarge)
            Text(
                "Локально сохранено $librarySize трек(ов). Поиск и импорт работают без отдельного экрана.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text("Поиск по библиотеке") },
                singleLine = true,
            )
            FilledTonalButton(onClick = onImportClick) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Импортировать файлы")
            }
        }
    }
}

@Composable
internal fun LuxTrackCard(
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onShowLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkThumb(track.artworkPath, modifier = Modifier.size(72.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                FilledTonalIconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!track.lyrics.isNullOrBlank()) {
                    AssistChip(
                        onClick = onShowLyrics,
                        label = { Text("Текст") },
                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                    )
                }
                AssistChip(
                    onClick = onAddToPlaylist,
                    label = { Text("В плейлист") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                )
                if (!track.sourceUrl.isNullOrBlank()) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Скачан по ссылке") },
                        leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LuxPlaylistHeaderCard(
    playlistsCount: Int,
    onCreatePlaylist: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Плейлисты", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$playlistsCount подборок сохранено локально.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onCreatePlaylist) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать")
            }
        }
    }
}

@Composable
internal fun LuxPlaylistCard(
    playlist: Playlist,
    tracksById: Map<String, Track>,
    onPlayPlaylist: () -> Unit,
) {
    val tracks = remember(playlist.trackIds, tracksById) { playlist.trackIds.mapNotNull(tracksById::get) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${playlist.trackIds.size} трек(ов)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onPlayPlaylist) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Играть")
                }
            }

            if (tracks.isNotEmpty()) {
                HorizontalDivider()
                tracks.take(4).forEach { track ->
                    Text(
                        text = "${track.title} • ${track.artist}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LuxDownloadCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    uiState: LuxMusicUiState,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Скачать по ссылке", style = MaterialTheme.typography.titleLarge)
            Text(
                "Поддержаны популярные URL популярных платформ. Реальный результат зависит от доступности публичного extractor/provider и прав на сохранение контента.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                serviceChips.forEach { service ->
                    AssistChip(
                        onClick = { },
                        label = { Text(service.title) },
                        leadingIcon = {
                            Icon(
                                if (service.availableNow) Icons.Rounded.OfflinePin else Icons.Rounded.Link,
                                contentDescription = null,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (service.availableNow) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка на трек, клип или релиз") },
                placeholder = { Text("https://...") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
            )
            Button(
                onClick = onDownload,
                enabled = uiState.download.isAvailable && !uiState.download.isRunning && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.download.isRunning) "Обрабатываем ссылку..." else "Скачать в офлайн")
            }
            if (uiState.download.isRunning) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { uiState.download.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                uiState.download.errorMessage ?: uiState.download.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.download.errorMessage == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
internal fun LuxEmptyStateCard(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
