package com.luxmusic.android.download

import com.luxmusic.android.data.DownloadService
import com.luxmusic.android.data.Track
import java.io.File

internal data class DownloadSourceMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val queryHint: String? = null,
)

internal data class DownloadSession(
    val cookiesText: String,
    val userAgent: String? = null,
)

internal enum class DownloadAttemptKind {
    DIRECT,
    MATCHED_SEARCH,
}

internal enum class ExtractorChannel {
    STABLE,
    NIGHTLY,
}

internal data class DownloadAttempt(
    val requestUrl: String,
    val requestService: DownloadService,
    val sourceService: DownloadService,
    val kind: DownloadAttemptKind,
    val expectedMetadata: DownloadSourceMetadata?,
    val label: String,
    val allowsNightlyRetry: Boolean,
)

internal data class DownloadPlan(
    val sourceUrl: String,
    val sourceService: DownloadService,
    val metadata: DownloadSourceMetadata?,
    val attempts: List<DownloadAttempt>,
)

internal data class DownloadExecutionResult(
    val tracks: List<Track>,
    val finalAttempt: DownloadAttempt,
)

internal interface MediaDownloadBackend {
    fun update(channel: ExtractorChannel)

    fun fetchInfo(
        url: String,
        service: DownloadService,
        session: DownloadSession?,
    ): DownloadSourceMetadata?

    fun download(
        requestUrl: String,
        service: DownloadService,
        session: DownloadSession?,
        outputDir: File,
        onProgress: (progress: Float, line: String?) -> Unit,
    )
}

internal interface MetadataHttpClient {
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String?
}

internal interface DownloadedTrackImporter {
    suspend fun importDownloadedFiles(
        audioFiles: List<File>,
        sourceUrl: String?,
        companionResolver: (File) -> List<File>,
    ): List<Track>
}

internal interface DownloadAudioInspector {
    fun probeDurationMs(file: File): Long
}

internal interface DownloadWorkspaceManager {
    fun createWorkspace(prefix: String): File

    fun cleanup(workspace: File)
}
