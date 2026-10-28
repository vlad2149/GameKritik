package ru.gamekritik.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File
import android.graphics.Bitmap

/** Explicit opt-in: creates one clearly labelled test account in the live project. */
class LiveRegistrationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun signupCreatesProfileAndRestoresSession() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSignup") == "true")
        val email = "gamekritik-check-${UUID.randomUUID()}@example.com"
        val password = "GK-${UUID.randomUUID()}"
        rule.onNodeWithText("Начать").performClick()
        rule.waitUntil(30000) { rule.onAllNodesWithText("The Witcher 3: Wild Hunt").fetchSemanticsNodes().isNotEmpty() }
        snapshot("catalog-live")
        rule.onNodeWithText("Профиль", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Создать аккаунт").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Проверка регистрации")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(email)
        rule.onAllNodes(hasSetTextAction())[2].performTextInput(password)
        rule.onAllNodes(hasSetTextAction())[3].performScrollTo().performTextInput(password)
        closeSoftKeyboard()
        rule.onNodeWithText("Зарегистрироваться").performScrollTo().performClick()
        rule.waitUntil(45000) { rule.onAllNodesWithText("The Witcher 3: Wild Hunt").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Профиль", useUnmergedTree = true).performClick()
        rule.onNodeWithText(email).assertExists()
        snapshot("registered-profile")
        runBlocking {
            val repository = SupabaseRepository(SessionStore(rule.activity))
            assertNotNull(repository.session)
            val profile = repository.profile()
            assertEquals(email, profile.email)
            assertTrue(profile.name.startsWith("Проверка"))
            assertTrue(repository.games().size >= 116)
            val game = repository.games().first()
            repository.favorite(game.id, true)
            assertTrue(game.id in repository.favorites())
            repository.favorite(game.id, false)
            repository.logout()
            repository.login(email, password)
            assertEquals(profile.id, repository.profile().id)
        }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(30000) { rule.onAllNodesWithText(email).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(email).assertExists()
    }
    private fun snapshot(name: String) {
        val folder = rule.activity.getExternalFilesDir(null)!!
        File(folder, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

