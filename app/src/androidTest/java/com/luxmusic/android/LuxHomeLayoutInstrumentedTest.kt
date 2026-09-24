package com.luxmusic.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmusic.android.data.PlaybackState
import com.luxmusic.android.data.Track
import com.luxmusic.android.ui.LuxHomePage
import com.luxmusic.android.ui.theme.LuxMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LuxHomeLayoutInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun playerControlsAndQueueFitWithoutScrollingOnCompactScreen() {
        val track = Track(
            id = "track-1",
            title = "Очень длинное название песни для проверки компактного плеера",
            artist = "Исполнитель",
            album = "Альбом",
            durationMs = 180_000L,
            localPath = "/unused/test.mp3",
            importedAt = 1L,
        )
        var queueOpens = 0
        composeRule.setContent {
            LuxMusicTheme {
                Box(Modifier.size(width = 360.dp, height = 420.dp)) {
                    LuxHomePage(
                        contentPadding = PaddingValues(0.dp),
                        uiState = LuxMusicUiState(
                            library = listOf(track),
                            currentTrack = track,
                            playback = PlaybackState(
                                currentTrackId = track.id,
                                queueTrackIds = listOf(track.id),
                                durationMs = track.durationMs,
                            ),
                        ),
                        onImportClick = {},
                        onDownloadClick = {},
                        onShowQueue = { queueOpens++ },
                        onTogglePlayback = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onSeekToFraction = {},
                        onShowLyrics = {},
                        onAddToPlaylist = {},
                        onEdit = {},
                        onPickArtwork = {},
                        onDelete = {},
                    )
                }
            }
        }

        listOf("Перемешать", "Предыдущий", "Играть", "Следующий", "Без повтора").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Открыть очередь").assertIsDisplayed().performClick()
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Подписывайтесь на канал разработки!").assertDoesNotExist()
        assertEquals(1, queueOpens)
    }
}
