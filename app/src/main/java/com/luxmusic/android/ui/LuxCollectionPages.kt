package com.luxmusic.android.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmusic.android.LuxMusicUiState
import com.luxmusic.android.data.DownloadAccountState
import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Playlist
import com.luxmusic.android.data.Track

private val downloadServices = listOf(
    "YouTube",
    "SoundCloud",
    "TikTok",
    "VK Музыка",
    "Яндекс Музыка",
    "Apple Music",
    "Spotify",
)

private val accountServices = listOf(
    DownloadService.YOUTUBE,
    DownloadService.YANDEX_MUSIC,
    DownloadService.VK_MUSIC,
)

@Composable
internal fun LuxLibraryPage(
    contentPadding: PaddingValues,
    query: String,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    librarySize: Int,
    onQueryChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onPlay: (String) -> Unit,
    onShowLyrics: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDelete: (Track) -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    Text("Локальная библиотека", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Сохранено $librarySize трек(ов) на устройстве. Один тап по треку запускает или ставит его на паузу.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text("Поиск по библиотеке") },
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = luxTonalButtonColors(),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Импортировать аудио")
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Библиотека пока пустая",
                    body = "Добавьте локальные файлы или скачайте музыку по ссылке, чтобы собрать офлайн-коллекцию.",
                )
            }
        } else {
            items(tracks, key = Track::id) { track ->
                val isCurrent = currentTrackId == track.id
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    onClick = { onPlay(track.id) },
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
                            ArtworkThumb(track.artworkPath, modifier = Modifier.size(72.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalIconButton(
                                onClick = { onPlay(track.id) },
                                colors = luxTonalIconButtonColors(),
                            ) {
                                Icon(
                                    if (isCurrent && isPlaying) {
                                        Icons.Rounded.PauseCircleFilled
                                    } else {
                                        Icons.Rounded.PlayCircleFilled
                                    },
                                    contentDescription = null,
                                )
                            }
                            IconButton(onClick = { onDelete(track) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!track.lyrics.isNullOrBlank()) {
                                AssistChip(
                                    onClick = { onShowLyrics(track) },
                                    label = { Text("Текст") },
                                    leadingIcon = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                                    colors = luxAssistChipColors(),
                                )
                            }
                            AssistChip(
                                onClick = { onAddToPlaylist(track) },
                                label = { Text("В плейлист") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                                colors = luxAssistChipColors(),
                            )
                            if (!track.sourceUrl.isNullOrBlank()) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Скачан по ссылке") },
                                    leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
                                    colors = luxSelectedAssistChipColors(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxPlaylistsPage(
    contentPadding: PaddingValues,
    playlists: List<Playlist>,
    tracksById: Map<String, Track>,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LuxInfoCard(
                title = "Плейлисты",
                body = "Открывайте подборки, добавляйте в них треки из библиотеки и запускайте воспроизведение с любого места.",
            )
        }

        if (playlists.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "Плейлистов пока нет",
                    body = "Создайте первую подборку, чтобы быстро собирать очереди под настроение или поездку.",
                )
            }
        } else {
            items(playlists, key = Playlist::id) { playlist ->
                val previewTracks = remember(playlist.trackIds, tracksById) {
                    playlist.trackIds.mapNotNull(tracksById::get)
                }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = luxCardColors(),
                    onClick = { onOpenPlaylist(playlist.id) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "${playlist.trackIds.size} трек(ов)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(
                                onClick = { onPlayPlaylist(playlist.id) },
                                colors = luxTonalButtonColors(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Играть")
                            }
                        }
                        if (previewTracks.isNotEmpty()) {
                            HorizontalDivider()
                            previewTracks.take(4).forEach { track ->
                                Text(
                                    text = "${track.title} • ${track.artist}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        AssistChip(
                            onClick = { onOpenPlaylist(playlist.id) },
                            label = { Text("Открыть плейлист") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                            colors = luxAssistChipColors(),
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCreatePlaylist,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                colors = luxPrimaryButtonColors(),
            ) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать плейлист")
            }
        }
    }
}

@Composable
internal fun LuxPlaylistDetailPage(
    contentPadding: PaddingValues,
    playlist: Playlist,
    tracks: List<Track>,
    onPlayPlaylist: () -> Unit,
    onPlayTrack: (String) -> Unit,
    onAddTracks: () -> Unit,
    onDeletePlaylist: () -> Unit,
) {
    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${playlist.trackIds.size} трек(ов) в подборке",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onPlayPlaylist,
                            colors = luxTonalButtonColors(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Играть всё")
                        }
                        OutlinedButton(onClick = onAddTracks) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить треки")
                        }
                        OutlinedButton(onClick = onDeletePlaylist) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Удалить")
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                LuxInfoCard(
                    title = "В плейлисте пока пусто",
                    body = "Нажмите «Добавить треки» и выберите музыку из локальной библиотеки.",
                )
            }
        } else {
            items(tracks, key = Track::id) { track ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = luxCardColors(),
                    onClick = { onPlayTrack(track.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkThumb(track.artworkPath, modifier = Modifier.size(64.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${track.artist} • ${track.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onPlayTrack(track.id) },
                            colors = luxTonalIconButtonColors(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuxDownloadPage(
    contentPadding: PaddingValues,
    url: String,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    uiState: LuxMusicUiState,
    onOpenDownloadAccountLogin: (DownloadService) -> Unit,
    onImportDownloadAccount: (DownloadService) -> Unit,
    onClearDownloadAccount: (DownloadService) -> Unit,
) {
    val accountsByService = remember(uiState.downloadAccounts) {
        uiState.downloadAccounts.associateBy(DownloadAccountState::service)
    }

    LazyColumn(
        contentPadding = pagePadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    Text("Скачать по ссылке", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "YouTube, TikTok и SoundCloud качаются напрямую через extractor. Yandex Music и VK сначала пробуются напрямую, а при неудаче переходят на поиск совпадения по метаданным. Apple Music и Spotify работают через извлечение метаданных и подбор офлайн-копии.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        downloadServices.forEach { service ->
                            AssistChip(
                                onClick = {},
                                label = { Text(service) },
                                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                                colors = luxSelectedAssistChipColors(),
                            )
                        }
                    }
                    accountServices.forEach { service ->
                        val accountState = accountsByService[service]
                            ?: DownloadAccountState(service = service, isConnected = false)
                        DownloadAccountCard(
                            service = service,
                            accountState = accountState,
                            onLogin = { onOpenDownloadAccountLogin(service) },
                            onImportCookies = { onImportDownloadAccount(service) },
                            onClear = { onClearDownloadAccount(service) },
                        )
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ссылка на трек, клип или релиз") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        singleLine = true,
                    )
                    Button(
                        onClick = onDownload,
                        enabled = uiState.download.isAvailable && !uiState.download.isRunning && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        colors = luxPrimaryButtonColors(),
                    ) {
                        Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.download.isRunning) "Обрабатываем ссылку..." else "Скачать в офлайн")
                    }
                    if (uiState.download.isRunning) {
                        LinearProgressIndicator(
                            progress = { uiState.download.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = uiState.download.errorMessage ?: uiState.download.statusMessage,
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
    }
}

@Composable
private fun DownloadAccountCard(
    service: DownloadService,
    accountState: DownloadAccountState,
    onLogin: () -> Unit,
    onImportCookies: () -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = luxCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(service.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            accountState.isConnected -> "Сессия подключена"
                            service == DownloadService.YANDEX_MUSIC || service == DownloadService.VK_MUSIC ->
                                "Сессия повышает шанс прямой загрузки"
                            service.accountRecommended -> "Аккаунт помогает обойти 429 и лимиты"
                            else -> "Аккаунт не обязателен"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (accountState.isConnected) "Подключено" else "Не подключено",
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    colors = if (accountState.isConnected) {
                        luxSelectedAssistChipColors()
                    } else {
                        luxAssistChipColors()
                    },
                )
            }
            Text(
                "Нажмите «Войти в приложении», авторизуйтесь прямо во встроенном окне и завершите вход. LuxMusic сам сохранит cookies сессии. Если конкретный сервис не даёт войти через WebView, ниже можно импортировать cookies вручную как fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onLogin,
                    colors = luxTonalButtonColors(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (accountState.isConnected) "Перевойти" else "Войти в приложении")
                }
                Button(
                    onClick = onImportCookies,
                    colors = luxPrimaryButtonColors(),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (accountState.isConnected) "Обновить cookies вручную" else "Импортировать cookies вручную")
                }
                if (accountState.isConnected) {
                    OutlinedButton(onClick = onClear) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отключить")
                    }
                }
            }
        }
    }
}
