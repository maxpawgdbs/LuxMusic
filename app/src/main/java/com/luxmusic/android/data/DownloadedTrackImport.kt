package com.luxmusic.android.data

import java.io.File

data class DownloadedTrackImport(
    val sourceId: String,
    val audioFile: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val artworkBytes: ByteArray? = null,
    val sourceUrl: String? = null,
)

data class DownloadedTrackImportResult(
    val sourceId: String,
    val track: Track,
)

data class PlaylistDraft(
    val name: String,
    val trackIds: List<String>,
)
