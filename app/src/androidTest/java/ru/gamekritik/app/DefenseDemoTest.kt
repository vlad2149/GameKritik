package ru.gamekritik.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real-device walkthrough. Uses unique test credentials from instrumentation arguments. */
@RunWith(AndroidJUnit4::class)
class DefenseDemoTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun pause() = Thread.sleep(750)
    private fun waitFor(text: String, timeout: Long = 40_000) = rule.waitUntil(timeout) {
        rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun openRedDead() {
        rule.onNodeWithContentDescription("Поиск игр").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Red Dead")
        waitFor("Red Dead Redemption 2")
        rule.onNodeWithText("Red Dead Redemption 2").performClick()
    }

    @Test fun registrationReviewsAndLoginOnPhone() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("defenseDemo") == "true")
        val email = requireNotNull(args.getString("demoEmail"))
        val password = requireNotNull(args.getString("demoPassword"))
        val original = "Проверка отзыва на настоящем телефоне GameKritik."
        val changed = "Изменённый отзыв на настоящем телефоне GameKritik."

        rule.onNodeWithText("Начать").performClick(); waitFor("85 игр"); pause()
        rule.onNodeWithText("Новые").performClick(); waitFor("Релизы 2025–2026"); pause()
        rule.onNodeWithText("Скоро выйдут").performClick(); waitFor("Metro 2039"); pause()
        rule.onNodeWithText("Популярные").performClick(); pause()
        openRedDead(); waitFor("В избранное"); pause()
        rule.onNodeWithText("Гайды").performScrollTo().performClick(); waitFor("Гайды и прохождения"); pause()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Моды").performScrollTo().performClick(); waitFor("Модификации (Моды)"); pause()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithContentDescription("Назад").performClick()

        rule.onNodeWithText("Войти").performClick()
        rule.onNodeWithText("Нет аккаунта? Регистрация").performClick(); waitFor("Зарегистрироваться"); pause()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Проверка защиты")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(email)
        rule.onAllNodes(hasSetTextAction())[2].performTextInput(password)
        rule.onAllNodes(hasSetTextAction())[3].performScrollTo().performTextInput(password)
        closeSoftKeyboard(); pause()
        rule.onNodeWithText("Зарегистрироваться").performScrollTo().performClick()
        waitFor("85 игр"); pause()
        rule.onNodeWithText("Профиль", useUnmergedTree = true).performClick()
        waitFor(email); pause()
        val repo = SupabaseRepository(SessionStore(rule.activity))
        runBlocking { assertEquals(email, repo.profile().email) }
        val redDeadId = runBlocking { repo.games().first { it.title == "Red Dead Redemption 2" }.id }

        rule.onNodeWithText("Каталог", useUnmergedTree = true).performClick()
        openRedDead(); pause()
        // The registration snackbar can cover the review button for several seconds.
        rule.waitUntil(12_000) {
            rule.onAllNodesWithText("Аккаунт создан", substring = true).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Отзывы").performScrollTo()
        rule.onNodeWithText("Отзывы").performClick()
        waitFor("Написать отзыв"); pause()
        rule.onNodeWithText("✎  Написать отзыв").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(original)
        rule.onNodeWithText("Сохранить").performClick()
        waitFor(original); pause()
        var reviewId = -1
        runBlocking {
            val review = repo.myReviews().first { it.gameId == redDeadId && it.text == original }
            reviewId = review.id
            assertEquals(8, review.rating)
        }

        rule.onNodeWithText("✎  Изменить свой отзыв").performClick()
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput(changed)
        rule.onNodeWithText("Сохранить").performClick()
        waitFor(changed); pause()
        runBlocking { assertTrue(repo.myReviews().any { it.id == reviewId && it.text == changed }) }

        rule.onNodeWithText("Профиль", useUnmergedTree = true).performClick()
        waitFor(changed); pause()
        rule.onNodeWithText("Удалить").performClick()
        waitFor("Удалить отзыв?"); pause()
        rule.onAllNodesWithText("Удалить").onLast().performClick()
        waitFor("Вы пока не написали отзывов"); pause()
        runBlocking { assertFalse(repo.myReviews().any { it.id == reviewId }) }

        rule.onNodeWithText("Выйти").performScrollTo().performClick()
        waitFor("Ваш игровой профиль"); pause()
        rule.onNodeWithText("Войти").performClick(); waitFor("Вход в аккаунт")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(email)
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(password)
        closeSoftKeyboard()
        rule.onNodeWithText("Войти").performClick()
        waitFor("85 игр"); pause()
        rule.onNodeWithText("Профиль", useUnmergedTree = true).performClick()
        waitFor(email); pause()
        runBlocking { assertEquals(email, SupabaseRepository(SessionStore(rule.activity)).profile().email) }
    }
}
