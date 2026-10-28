package ru.gamekritik.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only integration checks: no accounts or reviews are created. */
@RunWith(AndroidJUnit4::class)
class LiveCatalogTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun cloudCatalogMediaAndNavigation() = runBlocking {
        val repository = SupabaseRepository()
        val games = repository.games()
        assertEquals("Curated cloud catalog", 85, games.size)
        assertTrue(games.none { it.genre.split(" · ").contains("Инди") })
        val persona = games.first { it.title == "Persona 4 Revival" }
        assertTrue(persona.upcoming())
        assertEquals("2027-02-17", persona.release)
        val metro = games.first { it.title == "Metro 2039" }
        assertTrue(metro.upcoming())
        assertEquals("2027-02-04", metro.release)
        assertTrue(games.all { it.cover.startsWith("https://") })
        val witcher = games.first { it.title == "The Witcher 3: Wild Hunt" }
        val guides = repository.guides(witcher.id)
        assertTrue(guides.any { it.category == "Прохождения" && it.url.contains("steamcommunity.com") })
        assertTrue(repository.mods(witcher.id).any { it.url.contains("nexusmods.com") })
        assertEquals(4, repository.screenshots(witcher.id).size)
        val trailer = repository.trailers(witcher.id).first { it.url.contains(".m3u8") }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = ImageLoader(context).execute(ImageRequest.Builder(context).data(witcher.cover).build())
        assertTrue("Cover loads and decodes", result is SuccessResult)
        val ready = java.util.concurrent.CountDownLatch(1)
        var videoFailure: String? = null
        lateinit var player: androidx.media3.exoplayer.ExoPlayer
        rule.runOnUiThread {
            player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if(state == androidx.media3.common.Player.STATE_READY) ready.countDown() }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { videoFailure = error.message; ready.countDown() }
            })
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(trailer.url))
            player.prepare()
        }
        val loaded = ready.await(45,java.util.concurrent.TimeUnit.SECONDS)
        rule.runOnUiThread { player.release() }
        assertTrue("HLS trailer is ready", loaded)
        assertNull(videoFailure)
        rule.onNodeWithText("Начать").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("85 игр", substring=true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("artwork-loaded",useUnmergedTree=true).fetchSemanticsNodes().size >= 2 }
        snapshot("catalog-expanded")
        rule.onNodeWithText("Скоро выйдут").performClick()
        rule.onNodeWithText("Metro 2039").performScrollTo().assertExists()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("artwork-loaded",useUnmergedTree=true).fetchSemanticsNodes().size >= 2 }
        snapshot("upcoming")
        rule.onNodeWithText("Metro 2039").performClick()
        rule.onNodeWithText("Гайды").performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("До релиза: что проверить").fetchSemanticsNodes().isNotEmpty() }
        snapshot("guides-upcoming")
    }

    private fun snapshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}

