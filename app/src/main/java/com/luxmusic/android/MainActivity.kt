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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luxmusic.android.ui.LuxMusicScreen
import com.luxmusic.android.ui.theme.LuxMusicTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.reportActivityFailure("Не удалось обработать переданную ссылку.") {
            handleIncomingIntent(intent)
        }
        enableEdgeToEdge()
        viewModel.reportActivityFailure("Не удалось восстановить воспроизведение.") {
            viewModel.restorePlayback()
        }

        setContent {
            LuxMusicTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val actions = remember { UiActionGuards((application as LuxMusicApp).messages) }
                var pendingImportPlaylistName by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingTrackArtworkId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingPlaylistArtworkId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingArtistArtworkName by rememberSaveable { mutableStateOf<String?>(null) }
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    actions.run("Не удалось импортировать выбранные файлы.") {
                        viewModel.importAudio(uris, pendingImportPlaylistName)
                    }
                    pendingImportPlaylistName = null
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }
                val trackArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val trackId = pendingTrackArtworkId
                    if (uri != null && trackId != null) {
                        actions.run("Не удалось изменить обложку трека.") {
                            viewModel.updateTrackArtwork(trackId, uri)
                        }
                    }
                    pendingTrackArtworkId = null
                }
                val playlistArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val playlistId = pendingPlaylistArtworkId
                    if (uri != null && playlistId != null) {
                        actions.run("Не удалось изменить обложку плейлиста.") {
                            viewModel.updatePlaylistArtwork(playlistId, uri)
                        }
                    }
                    pendingPlaylistArtworkId = null
                }
                val artistArtworkLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                ) { uri ->
                    val artist = pendingArtistArtworkName
                    if (uri != null && artist != null) {
                        actions.run("Не удалось изменить обложку артиста.") {
                            viewModel.updateArtistArtwork(artist, uri)
                        }
                    }
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
                        viewModel.reportActivityFailure {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    viewModel.messages.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.authBrowserRequests.collect { request ->
                        actions.run("Не удалось открыть страницу авторизации Яндекс Музыки.") {
                            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText("Код Яндекс Музыки", request.userCode),
                            )
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url)))
                        }
                    }
                }

                LuxMusicScreen(
                    uiState = uiState.value,
                    snackbarHostState = snackbarHostState,
                    onSelectTab = actions.one("Не удалось открыть этот раздел.", viewModel::selectTab),
                    onSearchChange = actions.one("Не удалось обновить поиск.", viewModel::updateSearch),
                    onImportClick = { playlistName ->
                        pendingImportPlaylistName = playlistName
                        val launched = actions.run("Не удалось открыть выбор аудиофайлов.") {
                            importLauncher.launch(
                                arrayOf(
                                    "audio/*",
                                    "application/ogg",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        }
                        if (!launched) pendingImportPlaylistName = null
                    },
                    onCreatePlaylist = actions.one("Не удалось создать плейлист.", viewModel::createPlaylist),
                    onAddTrackToPlaylist = actions.two("Не удалось добавить трек в плейлист.", viewModel::addTrackToPlaylist),
                    onRemoveTrackFromPlaylist = actions.two("Не удалось удалить трек из плейлиста.", viewModel::removeTrackFromPlaylist),
                    onUpdatePlaylistName = actions.two("Не удалось переименовать плейлист.", viewModel::updatePlaylistName),
                    onUpdateTrackDetails = actions.three("Не удалось сохранить данные трека.", viewModel::updateTrackDetails),
                    onPickTrackArtwork = actions.one("Не удалось открыть выбор обложки.") { trackId ->
                        pendingTrackArtworkId = trackId
                        viewModel.reportActivityFailure { trackArtworkLauncher.launch("image/*") }
                    },
                    onPickPlaylistArtwork = actions.one("Не удалось открыть выбор обложки плейлиста.") { playlistId ->
                        pendingPlaylistArtworkId = playlistId
                        viewModel.reportActivityFailure { playlistArtworkLauncher.launch("image/*") }
                    },
                    onPickArtistArtwork = actions.one("Не удалось открыть выбор обложки артиста.") { artist ->
                        pendingArtistArtworkName = artist
                        viewModel.reportActivityFailure { artistArtworkLauncher.launch("image/*") }
                    },
                    onDeleteTrack = actions.one("Не удалось удалить трек.", viewModel::deleteTrack),
                    onDeletePlaylist = actions.one("Не удалось удалить плейлист.", viewModel::deletePlaylist),
                    onPlayTrack = actions.one("Не удалось включить трек.", viewModel::playTrack),
                    onPlayPlaylist = actions.one("Не удалось включить плейлист.", viewModel::playPlaylist),
                    onPlayPlaylistTrack = actions.two("Не удалось включить трек из плейлиста.", viewModel::playPlaylistTrack),
                    onPlayArtistTrack = actions.two("Не удалось включить трек артиста.", viewModel::playArtistTrack),
                    onSelectQueueTrack = actions.one("Не удалось переключить трек в очереди.", viewModel::selectQueueTrack),
                    onTogglePlayback = actions.zero("Не удалось изменить воспроизведение.") { viewModel.togglePlayback() },
                    onSkipPrevious = actions.zero("Не удалось включить предыдущий трек.") { viewModel.skipPrevious() },
                    onSkipNext = actions.zero("Не удалось включить следующий трек.") { viewModel.skipNext() },
                    onToggleShuffle = actions.zero("Не удалось изменить режим перемешивания.") { viewModel.toggleShuffle() },
                    onCycleRepeat = actions.zero("Не удалось изменить режим повтора.") { viewModel.cycleRepeat() },
                    onSeekToFraction = actions.one("Не удалось перемотать трек.", viewModel::seekToFraction),
                    onOpenExternalLink = actions.one("Не удалось открыть внешнюю ссылку.") { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onDownloadUrlChange = actions.one("Не удалось обновить ссылку.", viewModel::updateDownloadUrl),
                    onDownloadTitleChange = actions.one("Не удалось обновить название загрузки.", viewModel::updateDownloadTitle),
                    onDownloadLink = actions.three("Не удалось начать загрузку.", viewModel::downloadFromLink),
                    onConnectYandex = actions.zero("Не удалось начать вход в Яндекс Музыку.") { viewModel.connectYandexMusic() },
                    onDisconnectYandex = actions.zero("Не удалось отключить Яндекс Музыку.") { viewModel.disconnectYandexMusic() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.reportActivityFailure("Не удалось продолжить вход в Яндекс Музыку.") {
            viewModel.resumeYandexMusicAuthorization()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.reportActivityFailure("Не удалось обработать переданную ссылку.") {
            handleIncomingIntent(intent)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || !intent.type.orEmpty().startsWith("text/")) return
        viewModel.openSharedLink(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
