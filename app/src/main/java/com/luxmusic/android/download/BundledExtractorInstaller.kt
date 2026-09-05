package com.luxmusic.android.download

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** Installs the pinned extractor over the wrapper's obsolete bundle, retaining newer nightly updates. */
internal object BundledExtractorInstaller {
    const val VERSION = "2026.08.19"

    @Synchronized
    fun installIfNeeded(context: Context) {
        val target = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val installed = runCatching {
            ZipFile(target).use { zip ->
                zip.getEntry("yt_dlp/version.py")?.let { entry ->
                    zip.getInputStream(entry).bufferedReader().use { reader ->
                        val buffer = CharArray(4096)
                        val count = reader.read(buffer)
                        if (count <= 0) null else
                            Regex("__version__\\s*=\\s*['\"]([^'\"]+)['\"]")
                                .find(String(buffer, 0, count))?.groupValues?.get(1)
                    }
                }
            }
        }.getOrNull()
        if (!shouldInstall(installed)) return
        check(target.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
            "Не удалось подготовить загрузчик. Проверьте свободное место."
        }
        val atomic = AtomicFile(target)
        var output: FileOutputStream? = null
        try {
            output = atomic.startWrite()
            context.assets.open("yt-dlp").use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (error: Exception) {
            runCatching { output?.let(atomic::failWrite) }
            throw error
        }
    }

    internal fun shouldInstall(installedVersion: String?): Boolean {
        val date = installedVersion?.takeIf { it.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}(?:[.\\w-]*)")) }
            ?.take(10) ?: return true
        return date < VERSION
    }
}
