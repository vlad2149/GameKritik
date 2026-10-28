package ru.gamekritik.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import android.graphics.Bitmap
import java.io.File
import org.junit.runner.RunWith

/** Read-only check against Steam's public reviews endpoint. */
@RunWith(AndroidJUnit4::class)
class LiveSteamReviewsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun russianFunnyReviewsLinkToSteam() = runBlocking {
        val repository = SupabaseRepository()
        val game = repository.games().first { it.title == "The Witcher 3: Wild Hunt" }
        val reviews = repository.steamReviews(game)
        assertTrue("Steam supplied reviews", reviews.isNotEmpty())
        assertTrue("Sorted by funny votes", reviews.zipWithNext().all { (a, b) -> a.funnyVotes >= b.funnyVotes })
        assertTrue("Reviews link to Steam", reviews.all { it.url.startsWith("https://steamcommunity.com/profiles/") && it.url.contains("/recommended/292030/") })
    }

    @Test fun gamePageShowsSteamReviewCards() {
        rule.onNodeWithText("Начать").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Counter-Strike 2").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Counter-Strike 2").performClick()
        rule.onNodeWithText("Отзывы").performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Оригинал ↗").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Отзывы Steam").assertExists()
        rule.onNodeWithText("Отзывы Steam").performScrollTo()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(folder, "steam-reviews.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun profileLoadsOwnReviewHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SupabaseRepository(SessionStore(context))
        assumeTrue("Requires an existing signed-in test session", repository.session != null)
        val mine = repository.myReviews()
        assertTrue("My reviews have game IDs", mine.all { it.gameId > 0 })
        rule.onNodeWithText("Начать").performClick()
        rule.onNodeWithText("Профиль").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Мои отзывы").fetchSemanticsNodes().isNotEmpty() }
        if (mine.isEmpty()) rule.onNodeWithText("Вы пока не написали отзывов.", substring = true).assertExists()
    }
}
