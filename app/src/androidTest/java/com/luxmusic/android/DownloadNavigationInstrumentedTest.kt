package com.luxmusic.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rapidlySwitchesBetweenDownloadAndSettingsWithoutCrash() {
        repeat(30) {
            composeRule.onNodeWithContentDescription("Загрузка").performClick()
            composeRule.onNodeWithText("Загрузить файл").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Настройки").performClick()
            composeRule.onNodeWithText("Яндекс Музыка").assertIsDisplayed()
        }
    }
}
