package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.RepeatMode
import com.luxmusic.android.data.Track

@Composable
internal fun LuxHomePage(
    contentPadding: PaddingValues,
    uiState: LuxMusicUiState,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LuxPlayerCard(
                uiState = uiState,
                onTogglePlayback = onTogglePlayback,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onSeekToFraction = onSeekToFraction,
            )
        }
    }
}

@Composable
private fun LuxPlayerCard(
    uiState: LuxMusicUiState,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (currentTrack == null) {
                Text("Выберите трек в библиотеке", style = MaterialTheme.typography.headlineSmall)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 420.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ArtworkThumb(
                                currentTrack.artworkPath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                            LuxTrackMeta(
                                track = currentTrack,
                                queueTitle = uiState.playback.queueTitle,
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(currentTrack.artworkPath, modifier = Modifier.size(210.dp))
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleShuffle,
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
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий")
                    }
                    FilledIconButton(
                        onClick = onTogglePlayback,
                        colors = luxFilledIconButtonColors(),
                    ) {
                        Icon(
                            if (uiState.playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (uiState.playback.isPlaying) "Пауза" else "Играть",
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onSkipNext,
                        colors = luxTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий")
                    }
                    FilledTonalIconButton(
                        onClick = onCycleRepeat,
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
private fun LuxTrackMeta(
    track: Track,
    queueTitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LuxMarqueeText(
            text = track.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
        )
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
