package com.luxmusic.android.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryNavigationHeightTest {
    @Test
    fun scalesFormerBarHeightByOneAndAHalf() {
        assertEquals(84f, primaryNavigationHeight(200.dp).value, 0.01f)
        assertEquals(96f, primaryNavigationHeight(320.dp).value, 0.01f)
        assertEquals(120f, primaryNavigationHeight(400.dp).value, 0.01f)
        assertEquals(120f, primaryNavigationHeight(600.dp).value, 0.01f)
    }
}
