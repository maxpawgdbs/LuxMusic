package com.luxmusic.android.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.LuxTab

@Composable
fun LuxMusicScreen(
    uiState: LuxMusicUiState,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (LuxTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit,
    onUpdateTrackDetails: (String, String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayPlaylistTrack: (String, String) -> Unit,
    onSelectQueueTrack: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onDownloadUrlChange: (String) -> Unit,
    onDownloadLink: (String) -> Unit,
) {
    LuxMusicRoot(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSelectTab = onSelectTab,
        onSearchChange = onSearchChange,
        onImportClick = onImportClick,
        onCreatePlaylist = onCreatePlaylist,
        onAddTrackToPlaylist = onAddTrackToPlaylist,
        onUpdateTrackDetails = onUpdateTrackDetails,
        onDeleteTrack = onDeleteTrack,
        onDeletePlaylist = onDeletePlaylist,
        onPlayTrack = onPlayTrack,
        onPlayPlaylist = onPlayPlaylist,
        onPlayPlaylistTrack = onPlayPlaylistTrack,
        onSelectQueueTrack = onSelectQueueTrack,
        onTogglePlayback = onTogglePlayback,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        onSeekToFraction = onSeekToFraction,
        onDownloadUrlChange = onDownloadUrlChange,
        onDownloadLink = onDownloadLink,
    )
}
