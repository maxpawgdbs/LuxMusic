package com.luxmusic.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

class LibraryStore(private val context: Context) {
    private val writeMutex = Mutex()
    private val metadataExtractor = MetadataExtractor(context)

    private val storageRoot = File(context.filesDir, "luxmusic").apply { mkdirs() }
    private val tracksDir = File(storageRoot, "tracks").apply { mkdirs() }
    private val artworksDir = File(storageRoot, "artworks").apply { mkdirs() }
    private val manifestFile = File(storageRoot, "library.json")
    private val manifestAtomicFile = AtomicFile(manifestFile)

    private val mutableSnapshot = MutableStateFlow(loadSnapshot())
    val snapshot: StateFlow<LibrarySnapshot> = mutableSnapshot

    suspend fun importUris(uris: List<Uri>): List<Track> = withContext(Dispatchers.IO) {
        buildList {
            for (uri in uris) {
                if (isZipUri(uri)) {
                    addAll(runCatching { importZipUriInternal(uri) }.getOrDefault(emptyList()))
                } else {
                    runCatching { importUriInternal(uri) }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }
    }

    suspend fun importDownloadedFiles(
        audioFiles: List<File>,
        sourceUrl: String?,
        companionResolver: (File) -> List<File>,
    ): List<Track> = withContext(Dispatchers.IO) {
        val preparedTracks = buildList {
            audioFiles.forEach { audio ->
                runCatching {
                    prepareImportedTrack(
                        sourceFile = audio,
                        displayName = audio.name,
                        sourceUrl = sourceUrl,
                        companionFiles = companionResolver(audio),
                    )
                }.getOrNull()?.let(::add)
            }
        }
        persistImportedTracks(preparedTracks)
    }

    suspend fun importDownloadedTracks(items: List<DownloadedTrackImport>): List<DownloadedTrackImportResult> =
        withContext(Dispatchers.IO) {
            val preparedTracks = buildList {
                items.forEach { item ->
                    runCatching {
                        item.sourceId to prepareImportedTrack(
                            sourceFile = item.audioFile,
                            displayName = item.audioFile.name,
                            sourceUrl = item.sourceUrl,
                            companionFiles = emptyList(),
                            metadataOverride = item,
                        )
                    }.getOrNull()?.let(::add)
                }
            }
            val persisted = persistImportedTracks(preparedTracks.map { it.second })
            preparedTracks.zip(persisted).map { (prepared, track) ->
                DownloadedTrackImportResult(prepared.first, track)
            }
        }

    suspend fun importDownloadedArchive(
        archiveFile: File,
        sourceUrl: String?,
    ): List<Track> = withContext(Dispatchers.IO) {
        require(archiveFile.isFile) { "ZIP-архив не найден." }
        archiveFile.inputStream().use { source ->
            importZipStream(source = source, sourceUrl = sourceUrl)
        }
    }

    suspend fun createPlaylist(
        name: String,
        trackIds: List<String> = emptyList(),
    ): Playlist = withContext(Dispatchers.IO) {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            trackIds = trackIds.distinct(),
            createdAt = System.currentTimeMillis(),
        )

        writeMutex.withLock {
            val updated = mutableSnapshot.value.copy(
                playlists = (mutableSnapshot.value.playlists + playlist).sortedBy { it.name.lowercase() },
            )
            persist(updated)
        }

        playlist
    }

    suspend fun createPlaylists(drafts: List<PlaylistDraft>): List<Playlist> = withContext(Dispatchers.IO) {
        val createdAt = System.currentTimeMillis()
        val playlists = drafts.mapNotNull { draft ->
            val name = draft.name.trim()
            if (name.isBlank()) return@mapNotNull null
            Playlist(
                id = UUID.randomUUID().toString(),
                name = name,
                trackIds = draft.trackIds.distinct(),
                createdAt = createdAt,
            )
        }
        if (playlists.isEmpty()) return@withContext emptyList()

        writeMutex.withLock {
            val current = mutableSnapshot.value
            persist(
                current.copy(
                    playlists = (current.playlists + playlists).sortedBy { it.name.lowercase() },
                ),
            )
        }
        playlists
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val updatedPlaylists = mutableSnapshot.value.playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(trackIds = (playlist.trackIds + trackId).distinct())
                } else {
                    playlist
                }
            }

            persist(mutableSnapshot.value.copy(playlists = updatedPlaylists))
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val updatedPlaylists = mutableSnapshot.value.playlists.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
                } else {
                    playlist
                }
            }
            persist(mutableSnapshot.value.copy(playlists = updatedPlaylists))
        }
    }

    suspend fun updatePlaylistName(playlistId: String, name: String): Playlist? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val target = current.playlists.firstOrNull { it.id == playlistId } ?: return@withLock null
            val updatedPlaylist = target.copy(name = name.trim())
            val updatedPlaylists = current.playlists
                .map { playlist -> if (playlist.id == playlistId) updatedPlaylist else playlist }
                .sortedBy { it.name.lowercase() }
            persist(current.copy(playlists = updatedPlaylists))
            updatedPlaylist
        }
    }

    suspend fun updateTrackDetails(trackId: String, title: String, artist: String): Track? =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val current = mutableSnapshot.value
                val target = current.tracks.firstOrNull { it.id == trackId } ?: return@withLock null
                val updatedTrack = target.copy(
                    title = title.trim(),
                    artist = artist.trim(),
                )
                val updatedTracks = current.tracks.map { track ->
                    if (track.id == trackId) updatedTrack else track
                }
                val updatedArtistArtworks = current.artistArtworkPaths.toMutableMap()
                if (
                    !target.artist.equals(updatedTrack.artist, ignoreCase = true) &&
                    updatedTracks.none { it.artist.equals(target.artist, ignoreCase = true) }
                ) {
                    val previousArtwork = updatedArtistArtworks.remove(target.artist)
                    if (previousArtwork != null && updatedArtistArtworks[updatedTrack.artist] == null) {
                        updatedArtistArtworks[updatedTrack.artist] = previousArtwork
                    }
                }
                persist(
                    current.copy(
                        tracks = updatedTracks,
                        artistArtworkPaths = updatedArtistArtworks,
                    ),
                )
                updatedTrack
            }
        }

    suspend fun updateTrackArtwork(trackId: String, uri: Uri): Track? = withContext(Dispatchers.IO) {
        val artworkPath = storeArtwork(uri, "track-$trackId") ?: return@withContext null
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val target = current.tracks.firstOrNull { it.id == trackId }
            if (target == null) {
                File(artworkPath).delete()
                return@withLock null
            }
            val updatedTrack = target.copy(artworkPath = artworkPath)
            persist(
                current.copy(
                    tracks = current.tracks.map { track ->
                        if (track.id == trackId) updatedTrack else track
                    },
                ),
            )
            deleteReplacedArtwork(target.artworkPath, artworkPath)
            updatedTrack
        }
    }

    suspend fun updatePlaylistArtwork(playlistId: String, uri: Uri): Playlist? = withContext(Dispatchers.IO) {
        val artworkPath = storeArtwork(uri, "playlist-$playlistId") ?: return@withContext null
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val target = current.playlists.firstOrNull { it.id == playlistId }
            if (target == null) {
                File(artworkPath).delete()
                return@withLock null
            }
            val updatedPlaylist = target.copy(artworkPath = artworkPath)
            persist(
                current.copy(
                    playlists = current.playlists.map { playlist ->
                        if (playlist.id == playlistId) updatedPlaylist else playlist
                    },
                ),
            )
            deleteReplacedArtwork(target.artworkPath, artworkPath)
            updatedPlaylist
        }
    }

    suspend fun updateArtistArtwork(artist: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val normalizedArtist = artist.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val artworkPath = storeArtwork(uri, "artist") ?: return@withContext null
        writeMutex.withLock {
            val current = mutableSnapshot.value
            if (current.tracks.none { it.artist.equals(normalizedArtist, ignoreCase = true) }) {
                File(artworkPath).delete()
                return@withLock null
            }
            val existingKey = current.artistArtworkPaths.keys
                .firstOrNull { it.equals(normalizedArtist, ignoreCase = true) }
            val previousArtwork = existingKey?.let(current.artistArtworkPaths::get)
            val updatedArtworks = current.artistArtworkPaths.toMutableMap().apply {
                if (existingKey != null) remove(existingKey)
                put(normalizedArtist, artworkPath)
            }
            persist(current.copy(artistArtworkPaths = updatedArtworks))
            deleteReplacedArtwork(previousArtwork, artworkPath)
            artworkPath
        }
    }

    suspend fun deletePlaylist(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val removed = current.playlists.firstOrNull { it.id == playlistId } ?: return@withLock null
            persist(current.copy(playlists = current.playlists.filterNot { it.id == playlistId }))
            removed.artworkPath?.let { path -> runCatching { File(path).delete() } }
            removed
        }
    }

    suspend fun deleteTrack(trackId: String): Track? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val target = current.tracks.firstOrNull { it.id == trackId } ?: return@withLock null

            val remainingTracks = current.tracks.filterNot { it.id == trackId }
            val artistArtworkKey = if (
                remainingTracks.none { it.artist.equals(target.artist, ignoreCase = true) }
            ) {
                current.artistArtworkPaths.keys
                    .firstOrNull { it.equals(target.artist, ignoreCase = true) }
            } else {
                null
            }
            val artistArtworkPath = artistArtworkKey?.let(current.artistArtworkPaths::get)
            val updated = current.copy(
                tracks = remainingTracks,
                playlists = current.playlists.map { playlist ->
                    playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
                },
                artistArtworkPaths = if (artistArtworkKey != null) {
                    current.artistArtworkPaths - artistArtworkKey
                } else {
                    current.artistArtworkPaths
                },
            )

            persist(updated)

            runCatching { File(target.localPath).delete() }
            target.artworkPath?.let { path -> runCatching { File(path).delete() } }
            artistArtworkPath?.let { path -> runCatching { File(path).delete() } }

            target
        }
    }

    private suspend fun importUriInternal(uri: Uri): Track? {
        val displayName = queryDisplayName(uri) ?: "track-${System.currentTimeMillis()}.mp3"
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val extension = displayName.substringAfterLast('.', "").ifBlank {
            when (mimeType.lowercase()) {
                "audio/flac" -> "flac"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg", "application/ogg" -> "ogg"
                "audio/mp4", "audio/aac", "audio/aacp" -> "m4a"
                else -> "mp3"
            }
        }.lowercase()
        val sourceFile = File(tracksDir, "incoming-${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            sourceFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        return try {
            importFileInternal(
                sourceFile = sourceFile,
                displayName = displayName,
                sourceUrl = null,
                companionFiles = emptyList(),
            )
        } finally {
            sourceFile.delete()
        }
    }

    private suspend fun importZipUriInternal(uri: Uri): List<Track> {
        val source = context.contentResolver.openInputStream(uri) ?: return emptyList()
        return source.use { importZipStream(source = it, sourceUrl = null) }
    }

    private suspend fun importZipStream(
        source: InputStream,
        sourceUrl: String?,
    ): List<Track> {
        val preparedTracks = mutableListOf<Track>()
        var entriesRead = 0
        var totalBytes = 0L

        BufferedInputStream(source).use { buffered ->
            ZipInputStream(buffered).use { zip ->
                try {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entriesRead += 1
                        if (entriesRead > ImportFileRules.MAX_ZIP_ENTRIES) {
                            throw IllegalArgumentException("В ZIP-архиве слишком много файлов.")
                        }
                        if (!entry.isDirectory && entry.size > ImportFileRules.MAX_ZIP_ENTRY_BYTES) {
                            throw IllegalArgumentException("Файл в ZIP-архиве слишком большой.")
                        }

                        val displayName = ImportFileRules.supportedZipAudioName(
                            entryName = entry.name,
                            isDirectory = entry.isDirectory,
                            declaredSize = entry.size,
                        )
                        if (displayName != null) {
                            val extension = displayName.substringAfterLast('.').lowercase()
                            val temporaryFile = File(
                                tracksDir,
                                "incoming-${UUID.randomUUID()}.$extension",
                            )
                            try {
                                var entryBytes = 0L
                                temporaryFile.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        entryBytes += read
                                        totalBytes += read
                                        if (
                                            entryBytes > ImportFileRules.MAX_ZIP_ENTRY_BYTES ||
                                            totalBytes > ImportFileRules.MAX_ZIP_TOTAL_BYTES
                                        ) {
                                            throw IllegalArgumentException("ZIP-архив слишком большой.")
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                }
                                runCatching {
                                    prepareImportedTrack(
                                        sourceFile = temporaryFile,
                                        displayName = displayName,
                                        sourceUrl = sourceUrl,
                                        companionFiles = emptyList(),
                                    )
                                }.getOrNull()?.let(preparedTracks::add)
                            } finally {
                                temporaryFile.delete()
                            }
                        } else if (!entry.isDirectory) {
                            var entryBytes = 0L
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (
                                    entryBytes > ImportFileRules.MAX_ZIP_ENTRY_BYTES ||
                                    totalBytes > ImportFileRules.MAX_ZIP_TOTAL_BYTES
                                ) {
                                    throw IllegalArgumentException("ZIP-архив слишком большой.")
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                } catch (_: IOException) {
                    // Keep tracks already imported from a readable prefix of a damaged archive.
                } catch (_: IllegalArgumentException) {
                    // Keep tracks already imported from a valid prefix and stop on unsafe input.
                }
            }
        }
        return persistImportedTracks(preparedTracks)
    }

    private suspend fun importFileInternal(
        sourceFile: File,
        displayName: String,
        sourceUrl: String?,
        companionFiles: List<File>,
    ): Track? {
        val track = prepareImportedTrack(
            sourceFile = sourceFile,
            displayName = displayName,
            sourceUrl = sourceUrl,
            companionFiles = companionFiles,
        )
        return persistImportedTracks(listOf(track)).firstOrNull()
    }

    private fun prepareImportedTrack(
        sourceFile: File,
        displayName: String,
        sourceUrl: String?,
        companionFiles: List<File>,
        metadataOverride: DownloadedTrackImport? = null,
    ): Track {
        val id = UUID.randomUUID().toString()
        val fallbackExtension = sourceFile.extension.ifBlank { "mp3" }
        val extension = displayName.substringAfterLast('.', fallbackExtension).lowercase()
        val targetAudio = File(tracksDir, "$id.$extension")
        var artworkPath: String? = null
        return try {
            sourceFile.copyTo(targetAudio, overwrite = true)
            val metadata = metadataExtractor.fromFile(sourceFile, companionFiles)
            artworkPath = (metadataOverride?.artworkBytes ?: metadata.artworkBytes)?.let { bytes ->
                File(artworksDir, "$id.jpg").also { artworkFile ->
                    artworkFile.writeBytes(bytes)
                }.absolutePath
            }

            Track(
                id = id,
                title = metadataOverride?.title?.takeIf { it.isNotBlank() }
                    ?: metadata.title?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.'),
                artist = metadataOverride?.artist?.takeIf { it.isNotBlank() }
                    ?: metadata.artist?.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist",
                album = metadataOverride?.album?.takeIf { it.isNotBlank() }
                    ?: metadata.album?.takeIf { it.isNotBlank() }
                    ?: "Singles",
                durationMs = metadata.durationMs,
                localPath = targetAudio.absolutePath,
                artworkPath = artworkPath,
                lyrics = metadata.lyrics?.takeIf { it.isNotBlank() },
                sourceUrl = sourceUrl,
                importedAt = System.currentTimeMillis(),
            )
        } catch (error: Throwable) {
            targetAudio.delete()
            artworkPath?.let(::File)?.delete()
            throw error
        }
    }

    private suspend fun persistImportedTracks(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return emptyList()

        try {
            writeMutex.withLock {
                val updated = mutableSnapshot.value.copy(
                    tracks = (mutableSnapshot.value.tracks + tracks).sortedByDescending { it.importedAt },
                )
                persist(updated)
            }
        } catch (error: Throwable) {
            tracks.forEach { track ->
                File(track.localPath).delete()
                track.artworkPath?.let(::File)?.delete()
            }
            throw error
        }
        return tracks
    }

    private fun loadSnapshot(): LibrarySnapshot {
        if (!manifestAtomicFile.baseFile.exists()) return LibrarySnapshot()

        return runCatching {
            val json = manifestAtomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            LibrarySnapshot(
                tracks = root.optJSONArray("tracks").toTracks(),
                playlists = root.optJSONArray("playlists").toPlaylists(),
                artistArtworkPaths = root.optJSONObject("artistArtworks").toStringMap(),
            )
        }.getOrDefault(LibrarySnapshot())
    }

    private fun persist(snapshot: LibrarySnapshot) {
        val root = JSONObject()
            .put("tracks", JSONArray().apply { snapshot.tracks.forEach { put(it.toJson()) } })
            .put("playlists", JSONArray().apply { snapshot.playlists.forEach { put(it.toJson()) } })
            .put("artistArtworks", JSONObject(snapshot.artistArtworkPaths))

        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        var output: FileOutputStream? = null
        try {
            output = manifestAtomicFile.startWrite()
            output.write(bytes)
            manifestAtomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(manifestAtomicFile::failWrite)
            throw error
        }
        mutableSnapshot.value = snapshot
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
    }

    private fun isZipUri(uri: Uri): Boolean {
        val displayName = queryDisplayName(uri).orEmpty()
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        return displayName.endsWith(".zip", ignoreCase = true) ||
            mimeType.equals("application/zip", ignoreCase = true) ||
            mimeType.equals("application/x-zip-compressed", ignoreCase = true)
    }

    private fun JSONArray?.toTracks(): List<Track> {
        if (this == null) return emptyList()

        return List(length()) { index ->
            getJSONObject(index).toTrack()
        }
    }

    private fun JSONArray?.toPlaylists(): List<Playlist> {
        if (this == null) return emptyList()

        return List(length()) { index ->
            getJSONObject(index).toPlaylist()
        }
    }

    private fun JSONObject.toTrack(): Track = Track(
        id = getString("id"),
        title = getString("title"),
        artist = getString("artist"),
        album = getString("album"),
        durationMs = getLong("durationMs"),
        localPath = getString("localPath"),
        artworkPath = optStringOrNull("artworkPath"),
        lyrics = optStringOrNull("lyrics"),
        sourceUrl = optStringOrNull("sourceUrl"),
        importedAt = getLong("importedAt"),
    )

    private fun JSONObject.toPlaylist(): Playlist = Playlist(
        id = getString("id"),
        name = getString("name"),
        trackIds = optJSONArray("trackIds")?.let { array ->
            List(array.length()) { index -> array.getString(index) }
        }.orEmpty(),
        createdAt = getLong("createdAt"),
        artworkPath = optStringOrNull("artworkPath"),
    )

    private fun Track.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)
        .put("localPath", localPath)
        .putOpt("artworkPath", artworkPath)
        .putOpt("lyrics", lyrics)
        .putOpt("sourceUrl", sourceUrl)
        .put("importedAt", importedAt)

    private fun Playlist.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("trackIds", JSONArray().apply { trackIds.forEach(::put) })
        .put("createdAt", createdAt)
        .putOpt("artworkPath", artworkPath)

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { key -> getString(key) }
    }

    private fun storeArtwork(uri: Uri, prefix: String): String? {
        val temporaryFile = File(artworksDir, "incoming-${UUID.randomUUID()}")
        return try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                temporaryFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_ARTWORK_SOURCE_BYTES) return@use false
                        output.write(buffer, 0, read)
                    }
                    totalBytes > 0L
                }
            } ?: false
            if (!copied) return null

            val bitmap = decodeArtworkBitmap(temporaryFile) ?: return null
            val scale = minOf(
                1f,
                MAX_ARTWORK_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat(),
            )
            val outputBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val destination = File(artworksDir, "$prefix-${UUID.randomUUID()}.jpg")
            val compressed = destination.outputStream().use { output ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
            }
            if (outputBitmap !== bitmap) outputBitmap.recycle()
            bitmap.recycle()
            if (compressed) destination.absolutePath else {
                destination.delete()
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            temporaryFile.delete()
        }
    }

    private fun decodeArtworkBitmap(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longestSide = maxOf(width, height)
                if (longestSide > MAX_ARTWORK_DIMENSION) {
                    val scale = MAX_ARTWORK_DIMENSION.toFloat() / longestSide.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_ARTWORK_DIMENSION * 2 ||
            bounds.outHeight / sampleSize > MAX_ARTWORK_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private fun deleteReplacedArtwork(previousPath: String?, newPath: String) {
        if (!previousPath.isNullOrBlank() && previousPath != newPath) {
            runCatching { File(previousPath).delete() }
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) getString(name) else null
    }

    private companion object {
        const val MAX_ARTWORK_SOURCE_BYTES = 25L * 1024L * 1024L
        const val MAX_ARTWORK_DIMENSION = 1_600
        const val ARTWORK_JPEG_QUALITY = 90
    }

}
