package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track

@Composable
internal fun LuxHomePage(
    contentPadding: PaddingValues,
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    val queueTracks = remember(uiState.library, uiState.playback.queueTrackIds) {
        val tracksById = uiState.library.associateBy { it.id }
        uiState.playback.queueTrackIds.mapNotNull(tracksById::get)
    }

    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LuxPlayerCard(
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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = luxCardColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Коллекция", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LuxStatChip(Icons.Rounded.LibraryMusic, uiState.library.size.toString(), "Треков")
                        LuxStatChip(Icons.AutoMirrored.Rounded.QueueMusic, uiState.playlists.size.toString(), "Плейлистов")
                        LuxStatChip(Icons.Rounded.GraphicEq, formatDuration(uiState.playback.durationMs), "Очередь")
                    }
                    Text(
                        "Главная страница показывает активный плеер, текущую очередь и статус локальной коллекции без перегруженного фона.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Очередь", style = MaterialTheme.typography.titleLarge)
                    AssistChip(
                        onClick = {},
                        label = { Text(uiState.playback.queueTitle) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                        },
                        colors = luxSelectedAssistChipColors(),
                    )
                    if (queueTracks.isEmpty()) {
                        Text(
                            "Очередь появится после запуска трека или плейлиста.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        queueTracks.take(5).forEach { track ->
                            val isCurrent = track.id == uiState.currentTrack?.id
                            Text(
                                text = "${if (isCurrent) "• " else ""}${track.title} • ${track.artist}",
                                style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxPlayerCard(
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    val currentTrack = uiState.currentTrack

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = luxCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Сейчас играет",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (currentTrack == null) {
                Text("Добавьте музыку", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Импортируйте локальные файлы или скачайте трек по ссылке, чтобы начать воспроизведение.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onImportClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = luxTonalButtonColors(),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить музыку")
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 420.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(128.dp))
                            LuxTrackMeta(track = currentTrack, queueTitle = uiState.playback.queueTitle)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(128.dp))
                            LuxTrackMeta(
                                track = currentTrack,
                                queueTitle = uiState.playback.queueTitle,
                                modifier = Modifier.weight(1f),
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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = uiState.playback.shuffleEnabled,
                        onClick = onToggleShuffle,
                        label = { Text("Случайный порядок") },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                        colors = luxFilterChipColors(),
                    )
                    FilterChip(
                        selected = uiState.playback.repeatMode != RepeatMode.NONE,
                        onClick = onCycleRepeat,
                        label = { Text(repeatLabel(uiState.playback.repeatMode)) },
                        leadingIcon = {
                            Icon(
                                if (uiState.playback.repeatMode == RepeatMode.ONE) {
                                    Icons.Rounded.RepeatOne
                                } else {
                                    Icons.Rounded.Repeat
                                },
                                contentDescription = null,
                            )
                        },
                        colors = luxFilterChipColors(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onSkipPrevious,
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                    }
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        colors = luxFilledIconButtonColors(),
                    ) {
                        Icon(
                            if (uiState.playback.isPlaying) {
                                Icons.Rounded.PauseCircleFilled
                            } else {
                                Icons.Rounded.PlayCircleFilled
                            },
                            contentDescription = null,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onSkipNext,
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxTrackMeta(
    track: Track,
    queueTitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${track.artist} • ${track.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AssistChip(
            onClick = { },
            label = { Text(queueTitle) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
            colors = luxAssistChipColors(),
        )
    }
}
