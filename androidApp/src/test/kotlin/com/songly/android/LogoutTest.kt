package com.songly.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wylogowanie po zalogowaniu **w tej samej Activity**. ViewModel logowania żyje tyle
 * co Activity, więc po wylogowaniu wracał ze stanem „zalogowano" z poprzedniego
 * razu: ekran logowania od razu wpuszczał z powrotem, sesji już nie było, każde
 * żądanie dostawało 401, „sesja wygasła" wylogowywała — i tak w kółko, kilkaset
 * żądań na sekundę, z kręcącym się kółkiem na ekranie. Przy okazji formularz
 * pokazywał wpisane wcześniej hasło.
 *
 * Start zalogowany (jak w pozostałych testach Activity) tego nie widział, bo
 * ViewModel logowania nigdy nie powstawał — dlatego logujemy się tu przez UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestSonglyApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
class LogoutTest {

	private val backend = FakeBackend().apply {
		onJson("/api/v1/auth/login", """{"accessToken":"a","refreshToken":"r","tokenType":"Bearer","expiresInSeconds":900}""")
		onJson("/api/v1/feed", pageJson(postJson("p1", "Pierwszy post"), number = 0, totalPages = 1))
		onJson("/api/v1/profiles/me", profileJson())
		onJson("/api/v1/notifications/unread-count", """{"count":0}""")
		on("/api/v1/auth/logout", HttpStatusCode.NoContent) { "" }
	}

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val installBackend = object : ExternalResource() {
		override fun before() {
			TestSonglyApplication.backend = backend
			TestSonglyApplication.startLoggedIn = false
		}

		override fun after() {
			TestSonglyApplication.startLoggedIn = true
		}
	}

	@get:Rule(order = 2)
	val compose = createAndroidComposeRule<MainActivity>()

	private fun waitForText(text: String) {
		compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
	}

	@Test
	fun logoutAfterLoginStaysOnCleanLoginScreen() {
		waitForText("Nie masz konta? Zarejestruj się")
		compose.onNodeWithText("E-mail").performTextInput("ola@songly.pl")
		compose.onNodeWithText("Hasło").performTextInput("Songly123!")
		compose.onAllNodesWithText("Zaloguj się")[1].performClick()
		waitForText("Pierwszy post")

		// Wylogowanie siedzi w zakładce „Profil" dolnego paska.
		compose.onNodeWithText("Profil").performClick()
		compose.onNodeWithText("Wyloguj się").performScrollTo().performClick()
		waitForText("Nie masz konta? Zarejestruj się")
		val feedCalls = backend.callCount("/api/v1/feed")
		compose.mainClock.advanceTimeBy(2_000)
		compose.waitForIdle()

		compose.onNodeWithText("Pierwszy post").assertDoesNotExist()
		assertEquals(feedCalls, backend.callCount("/api/v1/feed"))
		compose.onNodeWithText("ola@songly.pl").assertDoesNotExist()
	}
}
