package com.luxmusic.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import android.Manifest
import android.os.Build
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNavigationInstrumentedTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun youtubeCookieSettingsAreRemoved() {
        composeRule.onNodeWithContentDescription("Загрузка").performClick()
        composeRule.onNodeWithContentDescription("Настройки").performClick()
        composeRule.onNodeWithText("Яндекс Музыка").assertIsDisplayed()
        composeRule.onNodeWithText("Импортировать cookies.txt").assertDoesNotExist()
        composeRule.onNodeWithText("Войти в YouTube").assertDoesNotExist()
    }

    @Test
    fun primaryNavigationIsCompactAndSettingsAreOnlyInDownload() {
        listOf("Главная", "Библиотека", "Артисты", "Плейлисты", "Загрузка").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }

        composeRule.onNodeWithContentDescription("Настройки").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Загрузка").performClick()
        composeRule.onNodeWithContentDescription("Настройки").performClick()
        composeRule.onNodeWithText("Яндекс Музыка").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").performClick()
        composeRule.onNodeWithText("Загрузить файл").assertIsDisplayed()
    }

    @Test
    fun bannersAreAtTheTopOfCollectionsAndAbsentFromHome() {
        composeRule.onNodeWithText("Подписывайтесь на канал разработки!").assertDoesNotExist()
        listOf("Библиотека", "Артисты", "Плейлисты").forEach { tab ->
            composeRule.onNodeWithContentDescription(tab).performClick()
            composeRule.onNodeWithText("Подписывайтесь на канал разработки!").assertIsDisplayed()
        }
    }

    @Test
    fun operationFailureAppearsOnScreenAndNavigationStillWorks() {
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        composeRule.runOnIdle { app.messages.report(java.io.IOException("Тестовая ошибка чтения")) }
        composeRule.onNodeWithText("Тестовая ошибка чтения").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Загрузка").performClick()
        composeRule.onNodeWithText("Загрузить файл").assertIsDisplayed()
    }

    @Test
    fun rapidlySwitchesBetweenDownloadAndSettingsWithoutCrash() {
        repeat(30) {
            composeRule.onNodeWithContentDescription("Загрузка").performClick()
            composeRule.onNodeWithText("Загрузить файл").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Настройки").performClick()
            composeRule.onNodeWithText("Яндекс Музыка").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Назад").performClick()
        }
    }

    @Test
    fun failedUiActionCanBeReportedAndScreenRemainsInteractive() {
        val app = ApplicationProvider.getApplicationContext<LuxMusicApp>()
        composeRule.runOnIdle {
            UiActionGuards(app.messages).zero("Не удалось открыть раздел") {
                throw java.io.IOException("Тестовый сбой интерфейса")
            }()
        }
        composeRule.onNodeWithText("Тестовый сбой интерфейса").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Загрузка").performClick()
        composeRule.onNodeWithText("Загрузить файл").assertIsDisplayed()
    }
}
