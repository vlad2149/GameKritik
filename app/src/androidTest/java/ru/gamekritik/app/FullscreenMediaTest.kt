package ru.gamekritik.app

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenMediaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun screenshotsFillLandscapeNavigateAndRestoreOrientation() {
        val previous = rule.activity.requestedOrientation
        rule.runOnUiThread {
            rule.activity.setContent {
                var open by remember { mutableStateOf(true) }
                MaterialTheme {
                    if(open) ScreenshotViewer(listOf(Media("One", ""), Media("Two", "")), 0) { open = false }
                    else Text("Viewer closed")
                }
            }
        }
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        rule.onNodeWithText("Скриншот 1 / 2").assertExists()
        rule.onNodeWithText("Следующий").performClick()
        rule.onNodeWithText("Скриншот 2 / 2").assertExists()
        val full = rule.onNodeWithTag("fullscreen-media").fetchSemanticsNode().boundsInRoot
        val image = rule.onNodeWithTag("fullscreen-image").fetchSemanticsNode().boundsInRoot
        assertEquals(full.width, image.width, 1f)
        assertEquals(full.height, image.height, 1f)
        rule.onNodeWithTag("fullscreen-image").performClick()
        rule.onNodeWithContentDescription("Закрыть полный экран").assertDoesNotExist()
        rule.onNodeWithTag("fullscreen-image").performClick()
        rule.onNodeWithContentDescription("Закрыть полный экран").performClick()
        rule.onNodeWithText("Viewer closed").assertExists()
        rule.runOnIdle { assertEquals(previous, rule.activity.requestedOrientation) }
    }

    @Test fun trailerFillsWindowAndClosesEvenOnPlaybackError() {
        val previous = rule.activity.requestedOrientation
        rule.runOnUiThread {
            rule.activity.setContent {
                var open by remember { mutableStateOf(true) }
                MaterialTheme {
                    if(open) TrailerPlayer(Media("Test", "file:///does-not-exist.mp4"), { open = false }, {})
                    else Text("Player closed")
                }
            }
        }
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        val full = rule.onNodeWithTag("fullscreen-media").fetchSemanticsNode().boundsInRoot
        val video = rule.onNodeWithTag("fullscreen-video").fetchSemanticsNode().boundsInRoot
        assertEquals(full.width, video.width, 1f)
        assertEquals(full.height, video.height, 1f)
        rule.onNodeWithContentDescription("Закрыть полный экран").performClick()
        rule.onNodeWithText("Player closed").assertExists()
        rule.runOnIdle { assertEquals(previous, rule.activity.requestedOrientation) }
    }
}
