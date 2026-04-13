package com.luxmusic.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track

@Composable
internal fun LuxOverviewSection(
    uiState: LuxMusicUiState,
    currentTrack: Track?,
    onImportClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LuxNowPlayingCard(
                    currentTrack = currentTrack,
                    uiState = uiState,
                    onImportClick = onImportClick,
                    onTogglePlayback = onTogglePlayback,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onSeekToFraction = onSeekToFraction,
                    modifier = Modifier.weight(1.35f),
                )
                LuxCollectionSummaryCard(
                    uiState = uiState,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LuxNowPlayingCard(
                    currentTrack = currentTrack,
                    uiState = uiState,
                    onImportClick = onImportClick,
                    onTogglePlayback = onTogglePlayback,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onSeekToFraction = onSeekToFraction,
                )
                LuxCollectionSummaryCard(uiState = uiState)
            }
        }
    }
}

@Composable
internal fun LuxNowPlayingCard(
    currentTrack: Track?,
    uiState: LuxMusicUiState,
    onImportClick: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
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
            Text(
                text = "Сейчас играет",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (currentTrack == null) {
                Text("Офлайн-библиотека пока пустая", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Импортируйте локальные файлы или сохраните трек по ссылке. LuxMusic хранит музыку на устройстве.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onImportClick) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить музыку")
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 430.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(116.dp))
                            LuxNowPlayingMeta(currentTrack = currentTrack, uiState = uiState)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(116.dp))
                            LuxNowPlayingMeta(
                                currentTrack = currentTrack,
                                uiState = uiState,
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = if (uiState.playback.shuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    FilledIconButton(onClick = onSkipPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                    }
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        AnimatedContent(targetState = uiState.playback.isPlaying, label = "playback-toggle") { isPlaying ->
                            Icon(
                                if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                                contentDescription = null,
                            )
                        }
                    }
                    FilledIconButton(onClick = onSkipNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    }
                    FilledTonalIconButton(onClick = onCycleRepeat) {
                        Icon(
                            if (uiState.playback.repeatMode == RepeatMode.ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = null,
                            tint = if (uiState.playback.repeatMode == RepeatMode.NONE) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxNowPlayingMeta(
    currentTrack: Track,
    uiState: LuxMusicUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = currentTrack.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${currentTrack.artist} • ${currentTrack.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AssistChip(
            onClick = { },
            label = { Text(uiState.playback.queueTitle) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
            },
        )
    }
}

@Composable
internal fun LuxCollectionSummaryCard(
    uiState: LuxMusicUiState,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
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
                LuxSummaryChip(Icons.Rounded.LibraryMusic, uiState.library.size.toString(), "Треков")
                LuxSummaryChip(Icons.AutoMirrored.Rounded.QueueMusic, uiState.playlists.size.toString(), "Плейлистов")
                LuxSummaryChip(Icons.Rounded.OfflinePin, "Local", "Хранение")
                LuxSummaryChip(
                    Icons.Rounded.Equalizer,
                    when (uiState.playback.repeatMode) {
                        RepeatMode.ALL -> "Queue"
                        RepeatMode.ONE -> "One"
                        RepeatMode.NONE -> "Off"
                    },
                    "Repeat",
                )
            }
            Text(
                "Интерфейс переведён на MD3: карточки, нижняя навигация, динамические цвета и адаптивная раскладка по ширине.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LuxSummaryChip(icon: ImageVector, value: String, label: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
