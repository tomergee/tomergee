package com.tomergee.mp3player.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomergee.mp3player.player.PlayerState
import com.tomergee.mp3player.player.RepeatMode
import com.tomergee.mp3player.track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerBarTest {
    @get:Rule val compose = createComposeRule()

    private val playing = PlayerState(
        currentTrack = track(1, title = "Hello"),
        isPlaying = true,
        positionMs = 65_000,
        durationMs = 200_000,
        hasNext = true,
        hasPrevious = true,
    )

    @Test
    fun `shows track info and times`() {
        compose.setContent { PlayerBar(playing, PlayerActions()) }
        compose.onNodeWithText("Hello").assertExists()
        compose.onNodeWithText("1:05").assertExists()
        compose.onNodeWithText("3:20").assertExists()
        compose.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun `hidden when nothing is queued`() {
        compose.setContent { PlayerBar(PlayerState(), PlayerActions()) }
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun `buttons invoke actions`() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            PlayerBar(
                playing,
                PlayerActions(
                    onPlayPause = { clicks += "playPause" },
                    onNext = { clicks += "next" },
                    onPrevious = { clicks += "previous" },
                    onToggleShuffle = { clicks += "shuffle" },
                    onCycleRepeat = { clicks += "repeat" },
                ),
            )
        }
        listOf("Pause", "Next", "Previous", "Shuffle off", "Repeat off").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        assertEquals(listOf("playPause", "next", "previous", "shuffle", "repeat"), clicks)
    }

    @Test
    fun `next disabled at end of queue and repeat state is labelled`() {
        compose.setContent { PlayerBar(playing.copy(hasNext = false, repeatMode = RepeatMode.ONE, isPlaying = false), PlayerActions()) }
        compose.onNodeWithContentDescription("Next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Repeat one").assertExists()
        compose.onNodeWithContentDescription("Play").assertExists()
    }
}
