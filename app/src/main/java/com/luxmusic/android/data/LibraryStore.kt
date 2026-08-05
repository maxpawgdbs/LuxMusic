package com.luxmusic.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.BufferedInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import java.io.IOException

class LibraryStore(private val context: Context) {
    private val writeMutex = Mutex()
    private val metadataExtractor = MetadataExtractor(context)

    private val storageRoot = File(context.filesDir, "luxmusic").apply { mkdirs() }
    private val tracksDir = File(storageRoot, "tracks").apply { mkdirs() }
    private val artworksDir = File(storageRoot, "artworks").apply { mkdirs() }
    private val manifestFile = File(storageRoot, "library.json")

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
        buildList {
            for (audio in audioFiles) {
                runCatching {
                    importFileInternal(
                        sourceFile = audio,
                        displayName = audio.name,
                        sourceUrl = sourceUrl,
                        companionFiles = companionResolver(audio),
                    )
                }.getOrNull()?.let(::add)
            }
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
                persist(current.copy(tracks = updatedTracks))
                updatedTrack
            }
        }

    suspend fun deletePlaylist(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val removed = current.playlists.firstOrNull { it.id == playlistId } ?: return@withLock null
            persist(current.copy(playlists = current.playlists.filterNot { it.id == playlistId }))
            removed
        }
    }

    suspend fun deleteTrack(trackId: String): Track? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = mutableSnapshot.value
            val target = current.tracks.firstOrNull { it.id == trackId } ?: return@withLock null

            val updated = current.copy(
                tracks = current.tracks.filterNot { it.id == trackId },
                playlists = current.playlists.map { playlist ->
                    playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
                },
            )

            persist(updated)

            runCatching { File(target.localPath).delete() }
            target.artworkPath?.let { path -> runCatching { File(path).delete() } }

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
        val imported = mutableListOf<Track>()
        val source = context.contentResolver.openInputStream(uri) ?: return emptyList()
        var entriesRead = 0
        var totalBytes = 0L

        BufferedInputStream(source).use { buffered ->
            ZipInputStream(buffered).use { zip ->
                try {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entriesRead += 1
                        if (entriesRead > ImportFileRules.MAX_ZIP_ENTRIES) break

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
                                importFileInternal(
                                    sourceFile = temporaryFile,
                                    displayName = displayName,
                                    sourceUrl = null,
                                    companionFiles = emptyList(),
                                )?.let(imported::add)
                            } finally {
                                temporaryFile.delete()
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
        return imported
    }

    private suspend fun importFileInternal(
        sourceFile: File,
        displayName: String,
        sourceUrl: String?,
        companionFiles: List<File>,
    ): Track? {
        val id = UUID.randomUUID().toString()
        val fallbackExtension = sourceFile.extension.ifBlank { "mp3" }
        val extension = displayName.substringAfterLast('.', fallbackExtension).lowercase()
        val targetAudio = File(tracksDir, "$id.$extension")
        sourceFile.copyTo(targetAudio, overwrite = true)

        val metadata = metadataExtractor.fromFile(sourceFile, companionFiles)
        val artworkPath = metadata.artworkBytes?.let { bytes ->
            File(artworksDir, "$id.jpg").also { artworkFile ->
                artworkFile.writeBytes(bytes)
            }.absolutePath
        }

        val track = Track(
            id = id,
            title = metadata.title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
            artist = metadata.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = metadata.album?.takeIf { it.isNotBlank() } ?: "Singles",
            durationMs = metadata.durationMs,
            localPath = targetAudio.absolutePath,
            artworkPath = artworkPath,
            lyrics = metadata.lyrics?.takeIf { it.isNotBlank() },
            sourceUrl = sourceUrl,
            importedAt = System.currentTimeMillis(),
        )

        runCatching {
            writeMutex.withLock {
                val updated = mutableSnapshot.value.copy(
                    tracks = (mutableSnapshot.value.tracks + track).sortedByDescending { it.importedAt },
                )
                persist(updated)
            }
        }.onFailure {
            targetAudio.delete()
            artworkPath?.let(::File)?.delete()
            return null
        }

        return track
    }

    private fun loadSnapshot(): LibrarySnapshot {
        if (!manifestFile.exists()) return LibrarySnapshot()

        return runCatching {
            val root = JSONObject(manifestFile.readText())
            LibrarySnapshot(
                tracks = root.optJSONArray("tracks").toTracks(),
                playlists = root.optJSONArray("playlists").toPlaylists(),
            )
        }.getOrDefault(LibrarySnapshot())
    }

    private fun persist(snapshot: LibrarySnapshot) {
        val root = JSONObject()
            .put("tracks", JSONArray().apply { snapshot.tracks.forEach { put(it.toJson()) } })
            .put("playlists", JSONArray().apply { snapshot.playlists.forEach { put(it.toJson()) } })

        manifestFile.writeText(root.toString(2))
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

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) getString(name) else null
    }

}
