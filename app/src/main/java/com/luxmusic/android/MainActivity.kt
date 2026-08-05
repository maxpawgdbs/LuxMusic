package com.luxmusic.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxmusic.android.ui.LuxMusicScreen
import com.luxmusic.android.ui.theme.LuxMusicTheme
import com.luxmusic.android.data.DownloadService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        enableEdgeToEdge()
        viewModel.restorePlayback()

        setContent {
            LuxMusicTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val uiScope = rememberCoroutineScope()
                var pendingImportPlaylistName by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingTrackArtworkId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingPlaylistArtworkId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingArtistArtworkName by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingCookieServiceName by rememberSaveable { mutableStateOf<String?>(null) }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    viewModel.importAudio(uris, pendingImportPlaylistName)
                    pendingImportPlaylistName = null
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }
                val cookiesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    val service = pendingCookieServiceName
                        ?.let { name -> runCatching { DownloadService.valueOf(name) }.getOrNull() }
                    if (service != null) viewModel.importDownloadAccountCookies(service, uri)
                    pendingCookieServiceName = null
                }
                val trackArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val trackId = pendingTrackArtworkId
                    if (uri != null && trackId != null) viewModel.updateTrackArtwork(trackId, uri)
                    pendingTrackArtworkId = null
                }
                val playlistArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val playlistId = pendingPlaylistArtworkId
                    if (uri != null && playlistId != null) viewModel.updatePlaylistArtwork(playlistId, uri)
                    pendingPlaylistArtworkId = null
                }
                val artistArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val artist = pendingArtistArtworkName
                    if (uri != null && artist != null) viewModel.updateArtistArtwork(artist, uri)
                    pendingArtistArtworkName = null
                }

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

                LaunchedEffect(Unit) {
                    viewModel.authBrowserRequests.collect { request ->
                        runCatching {
                            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText("Код Яндекс Музыки", request.userCode),
                            )
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url)))
                        }.onFailure {
                            snackbarHostState.showSnackbar(
                                "Код ${request.userCode} скопирован. Откройте ${request.url} в браузере.",
                            )
                        }
                    }
                }

                LuxMusicScreen(
                    uiState = uiState.value,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = viewModel::selectTab,
                    onSearchChange = viewModel::updateSearch,
                    onImportClick = { playlistName ->
                        pendingImportPlaylistName = playlistName
                        runCatching {
                            importLauncher.launch(
                                arrayOf(
                                    "audio/*",
                                    "application/ogg",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        }.onFailure { error ->
                            pendingImportPlaylistName = null
                            uiScope.launch {
                                snackbarHostState.showSnackbar(
                                    error.message ?: "Не удалось открыть выбор ZIP или аудиофайлов.",
                                )
                            }
                        }
                    },
                    onCreatePlaylist = viewModel::createPlaylist,
                    onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                    onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                    onUpdatePlaylistName = viewModel::updatePlaylistName,
                    onUpdateTrackDetails = viewModel::updateTrackDetails,
                    onPickTrackArtwork = { trackId ->
                        pendingTrackArtworkId = trackId
                        trackArtworkLauncher.launch("image/*")
                    },
                    onPickPlaylistArtwork = { playlistId ->
                        pendingPlaylistArtworkId = playlistId
                        playlistArtworkLauncher.launch("image/*")
                    },
                    onPickArtistArtwork = { artist ->
                        pendingArtistArtworkName = artist
                        artistArtworkLauncher.launch("image/*")
                    },
                    onDeleteTrack = viewModel::deleteTrack,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onPlayTrack = viewModel::playTrack,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onPlayPlaylistTrack = viewModel::playPlaylistTrack,
                    onPlayArtistTrack = viewModel::playArtistTrack,
                    onSelectQueueTrack = viewModel::selectQueueTrack,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onSeekToFraction = viewModel::seekToFraction,
                    onDownloadUrlChange = viewModel::updateDownloadUrl,
                    onDownloadTitleChange = viewModel::updateDownloadTitle,
                    onDownloadLink = viewModel::downloadFromLink,
                    onConnectYandex = viewModel::connectYandexMusic,
                    onDisconnectYandex = viewModel::disconnectYandexMusic,
                    onCaptureDownloadAccount = viewModel::captureDownloadAccountCookies,
                    onImportDownloadCookies = { service ->
                        pendingCookieServiceName = service.name
                        runCatching { cookiesLauncher.launch(arrayOf("text/plain", "*/*")) }
                            .onFailure { error ->
                                pendingCookieServiceName = null
                                uiScope.launch {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "Не удалось открыть выбор cookies.txt.",
                                    )
                                }
                            }
                    },
                    onClearDownloadAccount = viewModel::clearDownloadAccount,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeYandexMusicAuthorization()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || !intent.type.orEmpty().startsWith("text/")) return
        viewModel.openSharedLink(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
