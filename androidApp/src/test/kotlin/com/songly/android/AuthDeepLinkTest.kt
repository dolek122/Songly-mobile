package com.songly.android

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import io.ktor.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Linki z maili `songly://` (ADR 0037 backendu) otwierają aplikację w odpowiednim
 * miejscu. Test jedzie przez `MainActivity` z prawdziwym intentem, bo cała logika
 * siedzi na styku intentu, stanu sesji i nawigacji.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestSonglyApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
abstract class AuthDeepLinkTestBase(link: String, private val loggedIn: Boolean) {

	protected val verifyPath = "/api/v1/auth/register/verify"

	protected val backend = FakeBackend().apply {
		onJson(verifyPath, """{"accessToken":"a","refreshToken":"r","tokenType":"Bearer","expiresInSeconds":900}""")
		onJson("/api/v1/feed", pageJson(postJson("p1", "Pierwszy post"), number = 0, totalPages = 1))
		onJson("/api/v1/profiles/me", profileJson())
	}

	private val linkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
		.setClassName("com.songly.android", "com.songly.android.MainActivity")

	protected val compose = AndroidComposeTestRule(
		activityRule = ActivityScenarioRule<MainActivity>(linkIntent),
		activityProvider = { rule ->
			var activity: MainActivity? = null
			rule.scenario.onActivity { activity = it }
			activity!!
		},
	)

	// Atrapa i stan sesji muszą być gotowe, zanim reguła uruchomi Activity.
	@get:Rule
	val rules: TestRule = RuleChain
		.outerRule(MainDispatcherRule())
		.around(object : ExternalResource() {
			override fun before() {
				TestSonglyApplication.backend = backend
				TestSonglyApplication.startLoggedIn = loggedIn
			}
		})
		.around(compose)

	protected fun waitForText(text: String) {
		compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
	}

	protected fun verifyCalls() = backend.callCount(HttpMethod.Post, verifyPath)
}

class VerificationLinkWhenLoggedOutTest : AuthDeepLinkTestBase("songly://verify-email?token=abc123", loggedIn = false) {

	@Test
	fun verifiesAddressAndLogsIn() {
		waitForText("Pierwszy post")

		assertEquals(1, verifyCalls())
		assertEquals("""{"token":"abc123"}""", backend.lastBody(HttpMethod.Post, verifyPath))
	}

	/** Activity dostaje po obrocie ten sam intent — link nie może zadziałać drugi raz. */
	@Test
	fun rotationDoesNotReuseTheLink() {
		waitForText("Pierwszy post")

		compose.activityRule.scenario.recreate()
		waitForText("Pierwszy post")

		assertEquals(1, verifyCalls())
		assertEquals(0, compose.onAllNodesWithText("Otworzyć link z maila?").fetchSemanticsNodes().size)
	}
}

class ResetLinkWhenLoggedOutTest : AuthDeepLinkTestBase("songly://reset-password?token=xyz789", loggedIn = false) {

	/** Hasła nie zgadniemy — link otwiera formularz z tokenem, ale niczego nie wysyła. */
	@Test
	fun opensNewPasswordFormWithToken() {
		waitForText("Ustaw nowe hasło do swojego konta.")

		compose.onNodeWithText("xyz789").assertExists()
		assertEquals(0, backend.callCount("/api/v1/auth/password/reset"))
	}
}

class VerificationLinkWhenLoggedInTest : AuthDeepLinkTestBase("songly://verify-email?token=abc123", loggedIn = true) {

	/** Stary mail kliknięty przez pomyłkę nie może zabrać sesji bez pytania. */
	@Test
	fun asksBeforeLoggingOutAndCancelKeepsSession() {
		waitForText("Otworzyć link z maila?")
		compose.onNodeWithText("Anuluj").performClick()

		waitForText("Pierwszy post")
		assertEquals(0, verifyCalls())
	}

	@Test
	fun logoutContinuesWithTheLink() {
		waitForText("Otworzyć link z maila?")
		compose.onNodeWithText("Wyloguj się").performClick()

		compose.waitUntil(5_000) { verifyCalls() == 1 }
		assertEquals("""{"token":"abc123"}""", backend.lastBody(HttpMethod.Post, verifyPath))
	}
}
