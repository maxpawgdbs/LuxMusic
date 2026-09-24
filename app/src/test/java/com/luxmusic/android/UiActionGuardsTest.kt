package com.luxmusic.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UiActionGuardsTest {
    @Test
    fun zeroArgumentActionReportsFailureAndReturnsNormally() = runBlocking {
        val messages = AppMessages()
        var continued = false

        UiActionGuards(messages).zero("Не удалось выполнить действие") {
            throw IOException("Недоступно")
        }.invoke()
        continued = true

        assertTrue(continued)
        assertEquals("Недоступно", withTimeout(1_000) { messages.events.first() })
    }

    @Test
    fun oneArgumentActionForwardsItsValue() = runBlocking {
        val messages = AppMessages()
        var received = ""

        UiActionGuards(messages).one<String>("Ошибка") { received = it }.invoke("track-42")

        assertEquals("track-42", received)
    }

    @Test
    fun oneArgumentActionReportsFailure() = runBlocking {
        val messages = AppMessages()
        UiActionGuards(messages).one<String>("Не удалось удалить") { throw IOException("Нет доступа") }("x")
        assertEquals("Нет доступа", withTimeout(1_000) { messages.events.first() })
    }

    @Test
    fun twoArgumentActionForwardsBothValues() {
        val messages = AppMessages()
        var received: Pair<String, String>? = null

        UiActionGuards(messages).two<String, String>("Ошибка") { first, second ->
            received = first to second
        }("playlist", "track")

        assertEquals("playlist" to "track", received)
    }

    @Test
    fun twoArgumentActionReportsFailure() = runBlocking {
        val messages = AppMessages()
        UiActionGuards(messages).two<String, String>("Не удалось обновить") { _, _ ->
            throw IOException("Хранилище недоступно")
        }("playlist", "track")
        assertEquals("Хранилище недоступно", withTimeout(1_000) { messages.events.first() })
    }

    @Test
    fun threeArgumentActionForwardsEveryValue() {
        val messages = AppMessages()
        var received: Triple<String, String, String?>? = null

        UiActionGuards(messages).three<String, String, String?>("Ошибка") { first, second, third ->
            received = Triple(first, second, third)
        }("https://example.test/track", "Название", null)

        assertEquals(Triple("https://example.test/track", "Название", null), received)
    }

    @Test
    fun threeArgumentActionReportsFailure() = runBlocking {
        val messages = AppMessages()
        UiActionGuards(messages).three<String, String, String?>("Не удалось начать загрузку") { _, _, _ ->
            throw IOException("Сервис недоступен")
        }("url", "title", null)
        assertEquals("Сервис недоступен", withTimeout(1_000) { messages.events.first() })
    }

    @Test
    fun anActionCanRunAgainAfterFailure() = runBlocking {
        val messages = AppMessages()
        val guarded = UiActionGuards(messages).zero("Сбой") { throw IOException("Ошибка") }
        guarded()
        assertEquals("Ошибка", withTimeout(1_000) { messages.events.first() })

        var completed = false
        UiActionGuards(messages).zero("Сбой") { completed = true }()
        assertTrue(completed)
    }

    @Test
    fun cancellationIsNeverConvertedIntoAnErrorMessage() {
        val messages = AppMessages()
        val cancellation = CancellationException("screen closed")

        val thrown = runCatching {
            UiActionGuards(messages).zero("Сбой") { throw cancellation }()
        }.exceptionOrNull()

        assertTrue(thrown === cancellation)
    }
}
