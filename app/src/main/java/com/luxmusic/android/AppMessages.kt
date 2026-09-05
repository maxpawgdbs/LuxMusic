package com.luxmusic.android

import com.luxmusic.android.download.DownloadFailureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Keeps messages until the screen can display them, including failures during startup. */
class AppMessages {
    private val pending = Channel<String>(64, BufferOverflow.DROP_OLDEST)
    val events = pending.receiveAsFlow()
    val exceptionHandler = CoroutineExceptionHandler { _, error -> report(error) }

    fun emit(message: String) {
        if (message.isNotBlank()) pending.trySend(message.take(400))
    }

    fun report(error: Throwable, fallback: String = "Не удалось выполнить действие. Попробуйте ещё раз.") {
        if (error is CancellationException) return
        emit(DownloadFailureText.from(error, fallback))
    }

    inline fun attempt(fallback: String, action: () -> Unit) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            report(error, fallback)
        }
    }
}
