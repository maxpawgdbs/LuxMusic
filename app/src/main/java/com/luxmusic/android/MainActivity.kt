package com.luxmusic.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxmusic.android.data.DownloadService
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
                var pendingAccountImportService by rememberSaveable { mutableStateOf<DownloadService?>(null) }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    viewModel.importAudio(uris)
                }
                val accountCookiesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    pendingAccountImportService?.let { service ->
                        viewModel.importDownloadAccountCookies(service, uri)
                    }
                    pendingAccountImportService = null
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(Unit) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    viewModel.messages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LuxMusicScreen(
                    uiState = uiState.value,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = viewModel::selectTab,
                    onSearchChange = viewModel::updateSearch,
                    onImportClick = {
                        importLauncher.launch(
                            arrayOf(
                                "audio/*",
                                "application/ogg",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onCreatePlaylist = viewModel::createPlaylist,
                    onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                    onDeleteTrack = viewModel::deleteTrack,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onPlayTrack = viewModel::playTrack,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayPlaylistTrack = viewModel::playPlaylistTrack,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onSeekToFraction = viewModel::seekToFraction,
                    onDownloadLink = viewModel::downloadFromLink,
                    onCaptureDownloadAccount = viewModel::captureDownloadAccountCookies,
                    onImportDownloadAccount = { service ->
                        pendingAccountImportService = service
                        accountCookiesLauncher.launch(
                            arrayOf(
                                "text/plain",
                                "text/*",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onClearDownloadAccount = viewModel::clearDownloadAccount,
                )
            }
        }
    }
}
