package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadService
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.util.UUID

internal class YtDlpMediaDownloadBackend(
    private val context: Context,
) : MediaDownloadBackend {
    private val youtubeDl by lazy { YoutubeDL.getInstance() }
    private val ffmpeg by lazy { FFmpeg.getInstance() }
    @Volatile
    private var ffmpegInitialized = false

    fun initialize() {
        youtubeDl.init(context)
    }

    override fun update(channel: ExtractorChannel) {
        youtubeDl.updateYoutubeDL(
            context,
            when (channel) {
                ExtractorChannel.STABLE -> YoutubeDL.UpdateChannel._STABLE
                ExtractorChannel.NIGHTLY -> YoutubeDL.UpdateChannel._NIGHTLY
            },
        )
    }

    override fun fetchInfo(
        url: String,
        service: DownloadService,
        session: DownloadSession?,
    ): DownloadSourceMetadata? {
        val workspace = createWorkspace("info")
        return try {
            runCatching {
                youtubeDl.getInfo(
                    buildInfoRequest(
                        url = url,
                        jobDir = workspace,
                        service = service,
                        session = session,
                    ),
                ).toSourceMetadata()
            }.getOrNull()
        } finally {
            cleanup(workspace)
        }
    }

    override fun download(
        requestUrl: String,
        service: DownloadService,
        session: DownloadSession?,
        outputDir: File,
        onProgress: (progress: Float, line: String?) -> Unit,
    ) {
        if (requestProfileFor(service).extractAudio) {
            initializeFfmpegIfNeeded()
        }
        val jobId = "luxmusic-${UUID.randomUUID()}"
        youtubeDl.execute(
            buildDownloadRequest(
                url = requestUrl,
                jobDir = outputDir,
                service = service,
                session = session,
            ),
            jobId,
        ) { progress, _, line ->
            onProgress(progress, line)
        }
    }

    @Synchronized
    private fun initializeFfmpegIfNeeded() {
        if (ffmpegInitialized) return
        ffmpeg.init(context)
        ffmpegInitialized = true
    }

    private fun buildDownloadRequest(
        url: String,
        jobDir: File,
        service: DownloadService,
        session: DownloadSession?,
    ): YoutubeDLRequest {
        val profile = requestProfileFor(service)
        val request = YoutubeDLRequest(url)
            .addOption("-f", profile.formatSelector)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--restrict-filenames")
            .addOption("--no-part")
            .addOption("--no-mtime")
            .addOption("--abort-on-unavailable-fragments")
            .addOption("--concurrent-fragments", 4)
            .addOption("--retries", serviceRequestRetries(service))
            .addOption("--fragment-retries", serviceFragmentRetries(service))
            .addOption("--extractor-retries", serviceExtractorRetries(service))
            .addOption("--socket-timeout", serviceSocketTimeoutSeconds(service))
            .addOption("--sleep-requests", serviceSleepRequestsSeconds(service, session))
            .addOption("--write-thumbnail")
            .addOption("--write-info-json")
            .addOption("-o", jobDir.absolutePath + "/%(title).120B [%(id)s].%(ext)s")

        if (profile.extractAudio) {
            request
                .addOption("--extract-audio")
                .addOption("--audio-format", profile.targetAudioExtension ?: "best")
                .addOption("--audio-quality", "0")
        }

        return applySessionOptions(request, jobDir, service, session)
    }

    private fun buildInfoRequest(
        url: String,
        jobDir: File,
        service: DownloadService,
        session: DownloadSession?,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--socket-timeout", serviceSocketTimeoutSeconds(service))
            .addOption("--sleep-requests", serviceSleepRequestsSeconds(service, session))

        return applySessionOptions(request, jobDir, service, session)
    }

    private fun applySessionOptions(
        request: YoutubeDLRequest,
        jobDir: File,
        service: DownloadService,
        session: DownloadSession?,
    ): YoutubeDLRequest {
        if (session == null) return request

        val cookieFile = File(jobDir, "${service.name.lowercase()}-cookies.txt").apply {
            writeText(session.cookiesText)
        }
        request.addOption("--cookies", cookieFile.absolutePath)
        session.userAgent?.takeIf { it.isNotBlank() }?.let { userAgent ->
            request.addOption("--user-agent", userAgent)
        }
        return request
    }

    private fun VideoInfo.toSourceMetadata(): DownloadSourceMetadata = DownloadSourceMetadata(
        title = title.normalizedOrNull() ?: fulltitle.normalizedOrNull(),
        artist = uploader.normalizedOrNull(),
        durationMs = duration.takeIf { it > 0 }?.times(1_000L),
        queryHint = listOfNotNull(uploader.normalizedOrNull(), title.normalizedOrNull())
            .joinToString(" ")
            .takeIf { it.isNotBlank() },
    )

    private fun createWorkspace(prefix: String): File {
        return File(context.cacheDir, "luxmusic-$prefix-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    private fun cleanup(jobDir: File) {
        runCatching { jobDir.deleteRecursively() }
    }

    private fun serviceRequestRetries(service: DownloadService): Int = when (service) {
        DownloadService.YOUTUBE -> 5
        else -> 3
    }

    private fun serviceFragmentRetries(service: DownloadService): Int = 3

    private fun serviceExtractorRetries(service: DownloadService): Int = 2

    private fun serviceSocketTimeoutSeconds(service: DownloadService): Int = 20

    private fun serviceSleepRequestsSeconds(
        service: DownloadService,
        session: DownloadSession?,
    ): Int = when (service) {
        DownloadService.YOUTUBE -> if (session == null) 1 else 0
        else -> 0
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        internal fun requestProfileFor(service: DownloadService): YtDlpRequestProfile {
            return when (service) {
                DownloadService.YOUTUBE -> YtDlpRequestProfile(
                    formatSelector = AUDIO_ONLY_FORMAT,
                    extractAudio = false,
                    targetAudioExtension = null,
                )

                DownloadService.TIKTOK,
                DownloadService.UNKNOWN,
                -> YtDlpRequestProfile(
                    formatSelector = "$AUDIO_ONLY_FORMAT/best[acodec!=none]",
                    extractAudio = true,
                    targetAudioExtension = "best",
                )

                else -> YtDlpRequestProfile(
                    formatSelector = AUDIO_ONLY_FORMAT,
                    extractAudio = false,
                    targetAudioExtension = null,
                )
            }
        }

        private const val AUDIO_ONLY_FORMAT =
            "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best[acodec!=none]"
    }
}

internal data class YtDlpRequestProfile(
    val formatSelector: String,
    val extractAudio: Boolean,
    val targetAudioExtension: String?,
)
