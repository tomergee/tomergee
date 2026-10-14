package com.tomergee.mp3player

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * End-to-end playlist management through the real activity, database and player.
 * Runs on the JVM (Robolectric) via `./gradlew test` and on a device via `connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    private val permission = GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE,
    )
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule val rules: TestRule = RuleChain.outerRule(permission).around(compose)

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun createRenameAndDeletePlaylist() {
        compose.onNodeWithText("Playlists").performClick()
        waitForText("No playlists yet. Tap + to create one.")

        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNodeWithTag("playlistNameField").performTextInput("Road trip")
        compose.onNodeWithText("Create").performClick()
        waitForText("0 songs")

        compose.onNodeWithContentDescription("Options for Road trip").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithTag("playlistNameField").performTextClearance()
        compose.onNodeWithTag("playlistNameField").performTextInput("Gym")
        compose.onNodeWithText("Rename").performClick()
        waitForText("Gym")

        compose.onNodeWithText("Gym").performClick()
        waitForText("This playlist is empty. Add songs from the Songs tab.")
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Options for Gym").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        waitForText("No playlists yet. Tap + to create one.")
    }

    @Test
    fun startsOnSongsTab() {
        compose.onNode(hasText("Songs") and isSelected()).assertExists()
        compose.onNodeWithContentDescription("Rescan").assertExists()
    }
}
