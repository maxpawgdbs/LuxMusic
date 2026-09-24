package com.luxmusic.android

/** Converts synchronous failures at UI event boundaries into queued, user-visible messages. */
internal class UiActionGuards(private val messages: AppMessages) {
    fun run(fallback: String, action: () -> Unit): Boolean = messages.attempt(fallback, action)

    fun zero(fallback: String, action: () -> Unit): () -> Unit = {
        run(fallback, action)
        Unit
    }

    fun <A> one(fallback: String, action: (A) -> Unit): (A) -> Unit = { value ->
        run(fallback) { action(value) }
    }

    fun <A, B> two(fallback: String, action: (A, B) -> Unit): (A, B) -> Unit = { first, second ->
        run(fallback) { action(first, second) }
    }

    fun <A, B, C> three(fallback: String, action: (A, B, C) -> Unit): (A, B, C) -> Unit =
        { first, second, third -> run(fallback) { action(first, second, third) } }
}
