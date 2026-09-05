package com.luxmusic.android.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryCompatibilityInstrumentedTest {
    @Test fun oldLibrarySurvivesNewPlaylistAndReload() = runBlocking {
        isolated { context, manifest, audio ->
            val before = audio.readBytes()
            val store = LibraryStore(context)
            store.createPlaylist("Новый", listOf("old"))
            val restored = LibraryStore(context).snapshot.value
            assertEquals(audio.absolutePath, restored.tracks.single().localPath)
            assertEquals("old", restored.tracks.single().id)
            assertEquals(setOf("Старый", "Новый"), restored.playlists.map { it.name }.toSet())
            assertTrue(restored.playlists.all { it.trackIds == listOf("old") })
            assertArrayEquals(before, audio.readBytes())
            assertTrue(manifest.isFile)
        }
    }

    @Test fun interruptedLegacyAtomicWriteRestoresBakWithoutLosingMusic() = runBlocking {
        isolated { context, manifest, audio ->
            assertTrue(manifest.renameTo(File(manifest.path + ".bak")))
            val store = LibraryStore(context)
            assertEquals("old", store.snapshot.value.tracks.single().id)
            assertTrue(audio.isFile)
            assertTrue(manifest.isFile)
        }
    }

    @Test fun corruptedPrimaryRestoresBackupAndPreservesOriginal() = runBlocking {
        isolated { context, manifest, _ ->
            LibraryStore(context).createPlaylist("Новый")
            manifest.writeText("{interrupted")
            val warnings = mutableListOf<String>()
            val store = LibraryStore(context, warnings::add)
            assertEquals("old", store.snapshot.value.tracks.single().id)
            assertEquals(2, store.snapshot.value.playlists.size)
            assertTrue(warnings.isNotEmpty())
            assertTrue(manifest.parentFile!!.listFiles()!!.any { it.name.startsWith("library.corrupt-") })
            store.createPlaylist("После восстановления")
            assertEquals(3, LibraryStore(context).snapshot.value.playlists.size)
        }
    }

    @Test fun unreadableCatalogCannotBeOverwrittenByNewImportOrPlaylist() = runBlocking {
        isolated { context, manifest, audio ->
            val broken = "{broken"
            manifest.writeText(broken)
            val warnings = mutableListOf<String>()
            val store = LibraryStore(context, warnings::add)
            assertNotNull(runCatching { store.createPlaylist("Новый") }.exceptionOrNull())
            assertEquals(broken, manifest.readText())
            assertTrue(audio.isFile)
            assertTrue(warnings.isNotEmpty())
        }
    }

    private suspend fun isolated(block: suspend (Context, File, File) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "compatibility-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        val storage = File(root, "luxmusic").apply { mkdirs() }
        val audio = File(storage, "tracks/old.m4a").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val manifest = File(storage, "library.json")
        manifest.writeText("""{"tracks":[{"id":"old","title":"Песня","artist":"Артист","album":"Альбом",
            "durationMs":123000,"localPath":"${audio.absolutePath}","importedAt":10}],
            "playlists":[{"id":"old-list","name":"Старый","trackIds":["old"],"createdAt":1}]}""")
        try { block(context, manifest, audio) } finally { root.deleteRecursively() }
    }
}
