package com.luxmusic.android.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LibraryStoreImportInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun importsFortyWaveFilesFromOneArchiveWithoutCrashing() = runBlocking {
        val archive = File(context.cacheDir, "stress-${System.nanoTime()}.zip")
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                repeat(40) { index ->
                    zip.putNextEntry(ZipEntry("album-${index / 10}/track-$index.wav"))
                    zip.write(silentWave(durationMs = 120))
                    zip.closeEntry()
                }
            }

            val store = LibraryStore(context)
            val imported = store.importDownloadedArchive(archive, "https://example.test/music.zip")
            assertEquals(40, imported.size)
            assertTrue(imported.all { File(it.localPath).isFile && File(it.localPath).length() > 44L })
            imported.forEach { store.deleteTrack(it.id) }
        } finally {
            archive.delete()
        }
    }

    @Test
    fun rejectsArchiveWithoutAudioWithActionableMessage() = runBlocking {
        val archive = File(context.cacheDir, "empty-${System.nanoTime()}.zip")
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("not music".encodeToByteArray())
                zip.closeEntry()
            }

            val error = runCatching {
                LibraryStore(context).importDownloadedArchive(archive, null)
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("не найдено поддерживаемых аудиофайлов"))
        } finally {
            archive.delete()
        }
    }

    private fun silentWave(durationMs: Int): ByteArray {
        val sampleRate = 8_000
        val sampleCount = sampleRate * durationMs / 1_000
        val dataSize = sampleCount * 2
        return ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".encodeToByteArray())
                putInt(36 + dataSize)
                put("WAVEfmt ".encodeToByteArray())
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2.toShort())
                putShort(16.toShort())
                put("data".encodeToByteArray())
                putInt(dataSize)
                repeat(sampleCount) { putShort(0.toShort()) }
            }
            .array()
    }
}
