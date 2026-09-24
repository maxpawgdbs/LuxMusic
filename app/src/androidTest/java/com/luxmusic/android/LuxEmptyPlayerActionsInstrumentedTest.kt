package com.luxmusic.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.ui.LuxEmptyPlayerActions
import com.luxmusic.android.ui.theme.LuxMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LuxEmptyPlayerActionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryOffersOneTapImportAndDownloadActions() {
        val imports = AtomicInteger()
        val downloads = AtomicInteger()
        composeRule.setContent {
            LuxMusicTheme {
                LuxEmptyPlayerActions(
                    libraryIsEmpty = true,
                    onImportClick = { imports.incrementAndGet() },
                    onDownloadClick = { downloads.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText("Ваша музыка начинается здесь").assertIsDisplayed()
        composeRule.onNodeWithText("Добавить музыку").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Скачать по ссылке").assertIsDisplayed().performClick()

        assertEquals(1, imports.get())
        assertEquals(1, downloads.get())
    }

    @Test
    fun existingLibraryGetsHelpfulCopyAndSameQuickActions() {
        composeRule.setContent {
            LuxMusicTheme {
                LuxEmptyPlayerActions(libraryIsEmpty = false, onImportClick = {}, onDownloadClick = {})
            }
        }

        composeRule.onNodeWithText("Что послушаем дальше?").assertIsDisplayed()
        composeRule.onNodeWithText("Добавить музыку").assertIsDisplayed()
        composeRule.onNodeWithText("Скачать по ссылке").assertIsDisplayed()
        composeRule.onNodeWithText("Ваша музыка начинается здесь").assertDoesNotExist()
    }
}
