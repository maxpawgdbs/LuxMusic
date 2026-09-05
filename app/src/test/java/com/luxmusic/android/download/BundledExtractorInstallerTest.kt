package com.luxmusic.android.download

import org.junit.Assert.*
import org.junit.Test

class BundledExtractorInstallerTest {
    @Test fun `missing and obsolete extractors are replaced`() {
        listOf(null, "", "invalid", "2024.10.07", "2025.11.12").forEach {
            assertTrue(BundledExtractorInstaller.shouldInstall(it))
        }
    }

    @Test fun `current and newer nightly extractors are preserved`() {
        listOf("2026.08.19", "2026.08.19.232359", "2026.09.04", "2027.01.01").forEach {
            assertFalse(BundledExtractorInstaller.shouldInstall(it))
        }
    }
}
