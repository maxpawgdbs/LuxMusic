package com.luxmusic.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track

@Composable
internal fun LuxHomePage(
    contentPadding: PaddingValues,
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShowQueue: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onShowLyrics: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onPickArtwork: (Track) -> Unit,
    onDelete: (Track) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        LuxPlayerCard(
            uiState = uiState,
            onImportClick = onImportClick,
            onDownloadClick = onDownloadClick,
            onShowQueue = onShowQueue,
            onTogglePlayback = onTogglePlayback,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onSeekToFraction = onSeekToFraction,
            onShowLyrics = onShowLyrics,
            onAddToPlaylist = onAddToPlaylist,
            onEdit = onEdit,
            onPickArtwork = onPickArtwork,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun LuxPlayerCard(
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShowQueue: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onShowLyrics: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onPickArtwork: (Track) -> Unit,
    onDelete: (Track) -> Unit,
) {
    val currentTrack = uiState.currentTrack
    var menuExpanded by remember(currentTrack?.id) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = luxCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentTrack == null) {
                LuxEmptyPlayerActions(
                    libraryIsEmpty = uiState.library.isEmpty(),
                    onImportClick = onImportClick,
                    onDownloadClick = onDownloadClick,
                )
            } else {
                val trackActions: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onShowQueue,
                            enabled = uiState.playback.queueTrackIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Открыть очередь",
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с треком")
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
                                        onEdit(currentTrack)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Выбрать обложку") },
                                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onPickArtwork(currentTrack)
                                    },
                                )
                                if (!currentTrack.lyrics.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Текст песни") },
                                        leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onShowLyrics(currentTrack)
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
                                        onAddToPlaylist(currentTrack)
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
                                        onDelete(currentTrack)
                                    },
                                )
                            }
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 420.dp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(
                                currentTrack.artworkPath,
                                modifier = Modifier.size(108.dp),
                            )
                            LuxTrackMeta(
                                track = currentTrack,
                                queueTitle = uiState.playback.queueTitle,
                                modifier = Modifier.weight(1f),
                                actions = trackActions,
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(164.dp))
                            LuxTrackMeta(
                                track = currentTrack,
                                queueTitle = uiState.playback.queueTitle,
                                modifier = Modifier.weight(1f),
                                actions = trackActions,
                            )
                        }
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

                androidx.compose.material3.Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeekToFraction(sliderValue) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(uiState.playback.positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(uiState.playback.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(48.dp),
                        colors = if (uiState.playback.shuffleEnabled) {
                            luxFilledIconButtonColors()
                        } else {
                            luxTonalIconButtonColors()
                        },
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать")
                    }
                    FilledTonalIconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(48.dp),
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий")
                    }
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier.size(64.dp),
                        colors = luxFilledIconButtonColors(),
                    ) {
                        Crossfade(
                            targetState = uiState.playback.isPlaying,
                            label = "playback button",
                        ) { isPlaying ->
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Играть",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(48.dp),
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий")
                    }
                    FilledTonalIconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.size(48.dp),
                        colors = if (uiState.playback.repeatMode != RepeatMode.NONE) {
                            luxFilledIconButtonColors()
                        } else {
                            luxTonalIconButtonColors()
                        },
                    ) {
                        Icon(
                            if (uiState.playback.repeatMode == RepeatMode.ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = repeatLabel(uiState.playback.repeatMode),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxEmptyPlayerActions(
    libraryIsEmpty: Boolean,
    onImportClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (libraryIsEmpty) "Ваша музыка начинается здесь" else "Что послушаем дальше?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (libraryIsEmpty) {
                "Добавьте свои аудиофайлы или скачайте музыку по ссылке."
            } else {
                "Добавьте новые файлы или найдите трек по ссылке."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Добавить музыку")
        }
        OutlinedButton(
            onClick = onDownloadClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Скачать по ссылке")
        }
    }
}

@Composable
private fun LuxTrackMeta(
    track: Track,
    queueTitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LuxMarqueeText(
                text = track.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            actions()
        }
        Text(
            text = "${track.artist} • ${track.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            text = "Из: $queueTitle",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
