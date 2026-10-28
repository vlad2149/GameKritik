package ru.gamekritik.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class ScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun welcomeStartsApp() {
        var started = false
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Background, onSurface = Color.White)) { androidx.compose.material3.Surface(color = Background) { Welcome { started = true } } } }
        snapshot("welcome")
        rule.onNodeWithText("Начать").performClick()
        rule.runOnIdle { assertTrue(started) }
    }

    @Test fun gameDetailOpensScreenshotsAndTogglesFavorite() {
        var section = ""; var favorite = false
        val game = Game(1,"The Witcher 3: Wild Hunt","Описание игры","2015-05-19","CD Projekt RED","PC",9.8)
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Background, onSurface = Color.White)) { androidx.compose.material3.Surface(color = Background) { GameDetail(game,false,false,{favorite = true},{section = it}) } } }
        snapshot("game-detail")
        rule.onNodeWithText("♡  В избранное").performScrollTo().performClick()
        rule.onNodeWithText("Скриншоты").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(favorite); assertEquals("Скриншоты",section) }
    }

    @Test fun shortReviewCannotBeSubmitted() {
        rule.setContent { MaterialTheme { ReviewDialog(false,{}, { _, _ -> }) } }
        rule.onNodeWithText("Опубликовать").assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextInput("Хорошая игра с интересным сюжетом.")
        rule.onNodeWithText("Опубликовать").assertIsEnabled()
    }

    private fun snapshot(name: String) {
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        FileOutputStream(File(folder,"$name.png")).use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
