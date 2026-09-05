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
        messages.attempt("Не удалось открыть окно") { throw IllegalStateException() }
        assertEquals("Не удалось открыть окно", withTimeout(1_000) { messages.events.first() })
    }
}
