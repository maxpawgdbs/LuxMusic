package com.luxmusic.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track
import com.luxmusic.android.ui.theme.ArcticBlue
import com.luxmusic.android.ui.theme.CloudWhite
import com.luxmusic.android.ui.theme.MidnightBlue
import com.luxmusic.android.ui.theme.SoftSand
import com.luxmusic.android.ui.theme.SunsetOrange

@Composable
internal fun HeroPlayerCard(
    currentTrack: Track?,
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(ArcticBlue.copy(alpha = 0.92f), SunsetOrange.copy(alpha = 0.78f)),
                    ),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (currentTrack == null) {
                Text("Offline-first player", style = MaterialTheme.typography.titleLarge, color = MidnightBlue)
                Text(
                    "Добавьте первый трек, чтобы запустить локальную библиотеку, плейлисты и режимы проигрывания.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MidnightBlue.copy(alpha = 0.82f),
                )
                FilledIconButton(
                    onClick = onImportClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MidnightBlue,
                        contentColor = SoftSand,
                    ),
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkThumb(artworkPath = currentTrack.artworkPath, modifier = Modifier.size(104.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MidnightBlue,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MidnightBlue.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(uiState.playback.queueTitle) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                        )
                    }
                }

                var sliderValue by remember(
                    currentTrack.id,
                    uiState.playback.positionMs,
                    uiState.playback.durationMs,
                ) {
                    mutableFloatStateOf(
                        if (uiState.playback.durationMs > 0L) {
                            uiState.playback.positionMs.toFloat() / uiState.playback.durationMs.toFloat()
                        } else {
                            0f
                        },
                    )
                }

                Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeekToFraction(sliderValue) },
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(uiState.playback.positionMs), color = MidnightBlue)
                    Text(formatDuration(uiState.playback.durationMs), color = MidnightBlue)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = if (uiState.playback.shuffleEnabled) MidnightBlue else MidnightBlue.copy(alpha = 0.5f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(onClick = onSkipPrevious) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                        }
                        FilledIconButton(
                            onClick = onTogglePlayback,
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MidnightBlue,
                                contentColor = CloudWhite,
                            ),
                        ) {
                            AnimatedContent(targetState = uiState.playback.isPlaying, label = "play-state") { isPlaying ->
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                )
                            }
                        }
                        FilledIconButton(onClick = onSkipNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null)
                        }
                    }

                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (uiState.playback.repeatMode == RepeatMode.ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = null,
                            tint = if (uiState.playback.repeatMode == RepeatMode.NONE) {
                                MidnightBlue.copy(alpha = 0.5f)
                            } else {
                                MidnightBlue
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryActions(
    query: String,
    onQueryChange: (String) -> Unit,
    onImportClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text("Поиск по библиотеке") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        )
        FilledTonalButton(onClick = onImportClick) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить")
        }
    }
}

@Composable
internal fun TrackCard(
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onShowLyrics: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrent) 0.95f else 0.72f),
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
                ArtworkThumb(artworkPath = track.artworkPath, modifier = Modifier.size(70.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
        }
    }
}

@Composable
internal fun PlaylistCard(
    playlist: Playlist,
    tracksById: Map<String, Track>,
    onPlayPlaylist: () -> Unit,
) {
    val tracks = remember(playlist.trackIds, tracksById) { playlist.trackIds.mapNotNull(tracksById::get) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${playlist.trackIds.size} трек(ов)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(onClick = onPlayPlaylist) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
            }

            if (tracks.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
internal fun DownloadCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    uiState: LuxMusicUiState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Скачать по ссылке", style = MaterialTheme.typography.titleLarge)
            Text(
                "LuxMusic сохраняет результат локально на устройстве. Используйте только ссылки на музыку, которую вы имеете право скачивать.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка") },
                placeholder = { Text("https://...") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
            )
            Button(
                onClick = onDownload,
                enabled = uiState.download.isAvailable && !uiState.download.isRunning && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.download.isRunning) "Скачиваем..." else "Скачать и сохранить")
            }
            if (uiState.download.isRunning) {
                LinearProgressIndicator(
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
internal fun EmptyStateCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
