package com.luxmusic.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxmusic.android.ui.LuxMusicScreen
import com.luxmusic.android.ui.theme.LuxMusicTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LuxMusicTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    viewModel.importAudio(uris)
                }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LuxMusicScreen(
                    uiState = uiState.value,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = viewModel::selectTab,
                    onSearchChange = viewModel::updateSearch,
                    onImportClick = { importLauncher.launch(arrayOf("audio/*")) },
                    onCreatePlaylist = viewModel::createPlaylist,
                    onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                    onPlayTrack = viewModel::playTrack,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onSeekToFraction = viewModel::seekToFraction,
                    onDownloadLink = viewModel::downloadFromLink,
                )
            }
        }
    }
}

