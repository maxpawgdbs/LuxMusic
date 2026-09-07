package com.luxmusic.android.download

import org.junit.Assert.*
import org.junit.Test

class CatalogMetadataParserTest {
    @Test fun `deezer metadata never uses its preview URL`() {
        val metadata = CatalogMetadataParser.fromDeezer("""{"title":"Song","artist":{"name":"Artist"},"album":{"title":"Album"},"duration":210,"preview":"https://example.com/preview.mp3"}""")!!
        assertEquals("Artist Song", metadata.queryHint)
        assertEquals(210_000L, metadata.durationMs)
        assertEquals("Album", metadata.album)
    }
    @Test fun `deezer errors cannot become search queries`() {
        assertNull(CatalogMetadataParser.fromDeezer("""{"error":{"message":"Not found"}}"""))
        assertNull(CatalogMetadataParser.fromDeezer("not json"))
    }
    @Test fun `reads a public structured music recording`() {
        val metadata = CatalogMetadataParser.fromHtml("""<script type="application/ld+json">{"@graph":[{"@type":"MusicRecording","name":"Песня","byArtist":[{"name":"Артист"}],"duration":"PT3M30S","inAlbum":{"name":"Альбом"}}]}</script>""")!!
        assertEquals("Песня", metadata.title)
        assertEquals("Артист", metadata.artist)
        assertEquals(210_000L, metadata.durationMs)
    }
    @Test fun `does not arbitrarily select a track from a structured collection`() {
        assertNull(CatalogMetadataParser.fromHtml("""<script type="application/ld+json">[{"@type":"MusicRecording","name":"One"},{"@type":"MusicRecording","name":"Two"}]</script>"""))
    }
    @Test fun `login pages do not become music records`() {
        assertNull(CatalogMetadataParser.fromHtml("<html><title>Login</title></html>"))
    }

    @Test fun `recording can declare multiple schema types`() {
        val metadata = CatalogMetadataParser.fromHtml("""<script type="application/ld+json">{"@type":["CreativeWork","MusicRecording"],"name":"Song","byArtist":{"name":"Artist"}}</script>""")!!
        assertEquals("Artist", metadata.artist)
        assertEquals("Song", metadata.title)
    }
}
