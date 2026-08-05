package com.luxmusic.android.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DownloadFailureTextTest {
    @Test
    fun `uses nested actionable cause`() {
        val error = IllegalStateException("", IOException("Соединение сброшено"))
        assertEquals("Соединение сброшено", DownloadFailureText.from(error, "Ошибка"))
    }

    @Test
    fun `never exposes urls or oauth values`() {
        val text = DownloadFailureText.from(
            IllegalStateException("failed https://example.test/file?token=secret oauth=private"),
            "Ошибка",
        )
        assertFalse(text.contains("example.test"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("private"))
        assertTrue(text.contains("скрыт"))
    }

    @Test
    fun `explains memory exhaustion`() {
        assertTrue(
            DownloadFailureText.from(OutOfMemoryError(), "Ошибка")
                .contains("Недостаточно памяти"),
        )
    }
}
