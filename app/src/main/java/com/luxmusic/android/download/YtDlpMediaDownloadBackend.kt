package com.luxmusic.android.download

import android.content.Context
import com.luxmusic.android.data.DownloadService
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File

internal class YtDlpMediaDownloadBackend(
    private val context: Context,
) : MediaDownloadBackend {
    private val youtubeDl by lazy { YoutubeDL.getInstance() }

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
        val jobId = "luxmusic-${System.currentTimeMillis()}"
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

    private fun buildDownloadRequest(
        url: String,
        jobDir: File,
        service: DownloadService,
        session: DownloadSession?,
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption(
                "-f",
                "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio[acodec!=none]/best[acodec!=none]/bestaudio/best",
            )
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--restrict-filenames")
            .addOption("--no-part")
            .addOption("--abort-on-unavailable-fragments")
            .addOption("--retries", serviceRequestRetries(service))
            .addOption("--fragment-retries", serviceFragmentRetries(service))
            .addOption("--extractor-retries", serviceExtractorRetries(service))
            .addOption("--socket-timeout", serviceSocketTimeoutSeconds(service))
            .addOption("--sleep-requests", serviceSleepRequestsSeconds(service, session))
            .addOption("--write-thumbnail")
            .addOption("--write-info-json")
            .addOption("--write-auto-subs")
            .addOption("--sub-langs", "all")
            .addOption("-o", jobDir.absolutePath + "/%(title).140B.%(ext)s")

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
        DownloadService.YANDEX_MUSIC -> 15
        else -> 10
    }

    private fun serviceFragmentRetries(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 15
        else -> 10
    }

    private fun serviceExtractorRetries(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 6
        else -> 3
    }

    private fun serviceSocketTimeoutSeconds(service: DownloadService): Int = when (service) {
        DownloadService.YANDEX_MUSIC -> 60
        else -> 30
    }

    private fun serviceSleepRequestsSeconds(
        service: DownloadService,
        session: DownloadSession?,
    ): Int = when (service) {
        DownloadService.YOUTUBE -> if (session == null) 3 else 1
        else -> 1
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
