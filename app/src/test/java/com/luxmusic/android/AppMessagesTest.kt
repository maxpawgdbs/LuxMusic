package com.luxmusic.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppMessagesTest {
    @Test fun `startup error waits for screen and is consumed only once`() = runBlocking {
        val messages = AppMessages()
        messages.report(IOException("Файл недоступен"))
        assertEquals("Файл недоступен", withTimeout(1_000) { messages.events.first() })
        messages.emit("Следующее сообщение")
        assertEquals("Следующее сообщение", withTimeout(1_000) { messages.events.first() })
    }

    @Test fun `failed coroutine reports error and later actions still run`() = runBlocking {
        val messages = AppMessages()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + messages.exceptionHandler)
        try {
            scope.launch { throw IOException("Нет места") }.join()
            assertEquals("Нет места", withTimeout(1_000) { messages.events.first() })
            scope.launch { messages.emit("Готово") }.join()
            assertEquals("Готово", withTimeout(1_000) { messages.events.first() })
        } finally { scope.cancel() }
    }

    @Test fun `cancellation is rethrown without a notification`() = runBlocking {
        val messages = AppMessages()
        val cancelled = CancellationException("cancelled")
        val error = runCatching { messages.attempt("Ошибка") { throw cancelled } }.exceptionOrNull()
        assertSame(cancelled, error)
        assertSame(cancelled, runCatching { runCatchingCancellable { throw cancelled } }.exceptionOrNull())
        messages.report(cancelled)
        messages.emit("Готово")
        assertEquals("Готово", withTimeout(1_000) { messages.events.first() })
    }

    @Test fun `external activity errors become messages`() = runBlocking {
        val messages = AppMessages()
        assertFalse(messages.attempt("Не удалось открыть окно") { throw IllegalStateException() })
        assertEquals("Не удалось открыть окно", withTimeout(1_000) { messages.events.first() })
    }

    @Test fun `successful guarded action reports success and does not emit`() {
        val messages = AppMessages()
        var completed = false
        assertTrue(messages.attempt("Ошибка") { completed = true })
        assertTrue(completed)
    }

    @Test fun `message text is capped to a safe snackbar length`() = runBlocking {
        val messages = AppMessages()
        messages.emit("x".repeat(500))
        assertEquals(400, withTimeout(1_000) { messages.events.first().length })
    }

    @Test fun `blank messages are ignored`() = runBlocking {
        val messages = AppMessages()
        messages.emit("  \n")
        messages.emit("Видимое сообщение")
        assertEquals("Видимое сообщение", withTimeout(1_000) { messages.events.first() })
    }

    @Test fun `bounded queue drops oldest startup errors instead of growing without limit`() = runBlocking {
        val messages = AppMessages()
        repeat(70) { messages.emit("Ошибка $it") }
        assertEquals("Ошибка 6", withTimeout(1_000) { messages.events.first() })
    }
}
