package com.luxmusic.android.download.yandex

import java.net.URI

enum class YandexSourceKind {
    TRACK,
    ALBUM,
    ARTIST,
}

data class YandexMusicSource(
    val kind: YandexSourceKind,
    val entityId: Long,
    val albumId: Long? = null,
)

object YandexMusicUrlParser {
    private val allowedHosts = setOf(
        "music.yandex.ru",
        "music.yandex.com",
        "music.yandex.by",
        "music.yandex.kz",
        "music.yandex.uz",
        "music.yandex.az",
        "music.yandex.com.tr",
    )
    private val trackInAlbum = Regex("^/album/([1-9]\\d{0,18})/track/([1-9]\\d{0,18})/?$")
    private val track = Regex("^/track/([1-9]\\d{0,18})/?$")
    private val album = Regex("^/album/([1-9]\\d{0,18})/?$")
    private val artist = Regex("^/artist/([1-9]\\d{0,18})/?$")

    fun parse(value: String): YandexMusicSource {
        val normalized = value.trim()
        require(normalized.isNotEmpty() && normalized.none(Char::isWhitespace)) {
            "Вставьте одну полную ссылку Яндекс Музыки."
        }
        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("Некорректная ссылка Яндекс Музыки.") }
        require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
            "Ссылка Яндекс Музыки должна начинаться с https://."
        }
        val host = uri.host?.lowercase()?.removeSuffix(".")
        require(host in allowedHosts) { "Поддерживаются только ссылки Яндекс Музыки." }
        require(uri.userInfo == null && uri.port == -1) {
            "В ссылке не должны быть указаны учётные данные или порт."
        }

        trackInAlbum.matchEntire(uri.path.orEmpty())?.let { match ->
            return YandexMusicSource(
                kind = YandexSourceKind.TRACK,
                entityId = match.groupValues[2].toEntityId(),
                albumId = match.groupValues[1].toEntityId(),
            )
        }
        track.matchEntire(uri.path.orEmpty())?.let { match ->
            return YandexMusicSource(YandexSourceKind.TRACK, match.groupValues[1].toEntityId())
        }
        album.matchEntire(uri.path.orEmpty())?.let { match ->
            return YandexMusicSource(YandexSourceKind.ALBUM, match.groupValues[1].toEntityId())
        }
        artist.matchEntire(uri.path.orEmpty())?.let { match ->
            return YandexMusicSource(YandexSourceKind.ARTIST, match.groupValues[1].toEntityId())
        }
        throw IllegalArgumentException("Поддерживаются ссылки на трек, альбом или исполнителя.")
    }

    fun parseOrNull(value: String): YandexMusicSource? = runCatching { parse(value) }.getOrNull()

    fun hasYandexMusicHost(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.host?.lowercase()?.removeSuffix(".") in allowedHosts
    }

    private fun String.toEntityId(): Long = toLongOrNull()?.takeIf { it > 0L }
        ?: throw IllegalArgumentException("Некорректный идентификатор в ссылке Яндекс Музыки.")
}

data class YandexArtist(
    val id: Long,
    val name: String,
)

data class YandexTrack(
    val id: Long,
    val title: String,
    val artists: List<YandexArtist>,
    val albumId: Long?,
    val albumTitle: String?,
    val durationMs: Long,
    val coverUri: String?,
    val available: Boolean = true,
) {
    val artistNames: String
        get() = artists.map(YandexArtist::name).filter(String::isNotBlank).distinct().joinToString(", ")
            .ifBlank { "Неизвестный исполнитель" }
}

data class YandexAlbum(
    val id: Long,
    val title: String,
    val type: String?,
    val artistIds: List<Long>,
    val tracks: List<YandexTrack> = emptyList(),
)

data class YandexAlbumPage(
    val albums: List<YandexAlbum>,
    val page: Int,
    val perPage: Int,
    val total: Int,
)

data class YandexResolvedAlbum(
    val id: Long?,
    val title: String,
    val tracks: List<YandexTrack>,
)

data class YandexResolvedCollection(
    val label: String,
    val sourceKind: YandexSourceKind,
    val albums: List<YandexResolvedAlbum>,
) {
    val tracks: List<YandexTrack>
        get() = albums.flatMap(YandexResolvedAlbum::tracks).distinctBy(YandexTrack::id)
}

interface YandexCatalogApi {
    suspend fun track(id: Long): YandexTrack?
    suspend fun albumWithTracks(id: Long): YandexAlbum?
    suspend fun artist(id: Long): YandexArtist?
    suspend fun directAlbums(artistId: Long, page: Int, pageSize: Int): YandexAlbumPage?
}

class YandexCatalogResolver(
    private val api: YandexCatalogApi,
    private val maxTracks: Int = 1_000,
) {
    suspend fun resolve(source: YandexMusicSource): YandexResolvedCollection {
        return when (source.kind) {
            YandexSourceKind.TRACK -> resolveTrack(source.entityId)
            YandexSourceKind.ALBUM -> resolveAlbum(source.entityId)
            YandexSourceKind.ARTIST -> resolveArtist(source.entityId)
        }
    }

    private suspend fun resolveTrack(id: Long): YandexResolvedCollection {
        val track = api.track(id)?.takeIf(YandexTrack::available)
            ?: error("Трек не найден или недоступен аккаунту.")
        return YandexResolvedCollection(
            label = track.title,
            sourceKind = YandexSourceKind.TRACK,
            albums = listOf(
                YandexResolvedAlbum(track.albumId, track.albumTitle ?: "Синглы", listOf(track)),
            ),
        )
    }

    private suspend fun resolveAlbum(id: Long): YandexResolvedCollection {
        val album = api.albumWithTracks(id)
            ?: error("Альбом не найден или недоступен аккаунту.")
        val tracks = album.tracks
            .filter(YandexTrack::available)
            .distinctBy(YandexTrack::id)
            .map { it.copy(albumId = album.id, albumTitle = album.title) }
        require(tracks.isNotEmpty()) { "В альбоме нет доступных треков." }
        checkLimit(tracks.size)
        return YandexResolvedCollection(
            label = album.title,
            sourceKind = YandexSourceKind.ALBUM,
            albums = listOf(YandexResolvedAlbum(album.id, album.title, tracks)),
        )
    }

    private suspend fun resolveArtist(id: Long): YandexResolvedCollection {
        val artist = api.artist(id) ?: error("Исполнитель не найден или недоступен аккаунту.")
        val summaries = mutableListOf<YandexAlbum>()
        var pageNumber = 0
        do {
            val page = api.directAlbums(id, pageNumber, PAGE_SIZE) ?: break
            summaries += page.albums.filter { album ->
                album.type.orEmpty().lowercase() in allowedAlbumTypes &&
                    (album.artistIds.isEmpty() || id in album.artistIds)
            }
            pageNumber += 1
            val hasNextPage = page.perPage > 0 && pageNumber * page.perPage < page.total
        } while (hasNextPage)

        val resolvedAlbums = mutableListOf<YandexResolvedAlbum>()
        val uniqueTrackIds = mutableSetOf<Long>()
        for (summary in summaries.distinctBy(YandexAlbum::id)) {
            val album = api.albumWithTracks(summary.id) ?: continue
            val tracks = album.tracks
                .filter(YandexTrack::available)
                .distinctBy(YandexTrack::id)
                .map { it.copy(albumId = album.id, albumTitle = album.title) }
            if (tracks.isEmpty()) continue
            uniqueTrackIds += tracks.map(YandexTrack::id)
            checkLimit(uniqueTrackIds.size)
            resolvedAlbums += YandexResolvedAlbum(album.id, album.title, tracks)
        }
        require(resolvedAlbums.isNotEmpty()) {
            "В прямой дискографии исполнителя нет доступных альбомов, EP или синглов."
        }
        return YandexResolvedCollection(artist.name, YandexSourceKind.ARTIST, resolvedAlbums)
    }

    private fun checkLimit(count: Int) {
        require(count <= maxTracks) { "Найдено больше $maxTracks треков. Загрузите меньшую коллекцию." }
    }

    private companion object {
        const val PAGE_SIZE = 50
        val allowedAlbumTypes = setOf("", "album", "ep", "single")
    }
}
