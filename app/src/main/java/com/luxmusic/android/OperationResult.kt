package com.luxmusic.android

import kotlinx.coroutines.CancellationException

/** Cancellation must never start a retry or get reported as an ordinary operation failure. */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
