package com.luxmusic.android.download

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ExtractorRecoveryPolicyTest {
    @Test fun `network and access errors never trigger updates`() {
        listOf("Unable to extract: HTTP 429", "Extractor error: HTTP 403", "Read timed out",
            "Unable to extract private video", "Sign in to access", "DRM protected", "No space left on device",
            "Unable to extract: failed to resolve host", "subscriber only", "Requested format is not available").forEach {
            assertFalse(it, ExtractorRecoveryPolicy.shouldUpdate(IllegalStateException(it)))
        }
    }
    @Test fun `known extractor breakage may update once`() {
        listOf("outdated extractor", "Unable to extract signature", "JavaScript challenge failed",
            "Unable to extract player response").forEach {
            assertTrue(it, ExtractorRecoveryPolicy.shouldUpdate(IOException(it)))
        }
    }
    @Test fun `nested network failure wins over generic extractor wrapper`() {
        assertFalse(ExtractorRecoveryPolicy.shouldUpdate(IllegalStateException("Extractor failed", IOException("Connection reset"))))
    }
}
