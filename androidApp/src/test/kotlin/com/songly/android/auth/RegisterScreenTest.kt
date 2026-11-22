package com.songly.android.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RegisterScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private val tokensJson =
		"""{"accessToken":"a","refreshToken":"r","tokenType":"Bearer","expiresInSeconds":900}"""

	private fun backend() = FakeBackend().apply {
		on("/api/v1/auth/register", HttpStatusCode.Accepted) { "" }
		onJson("/api/v1/auth/register/verify", tokensJson)
	}

	private fun show(backend: FakeBackend, onRegistered: () -> Unit = {}) {
		compose.setContent {
			RegisterScreen(RegisterViewModel(backend.client()), onRegistered = onRegistered, onNavigateToLogin = {})
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun fillForm() {
		compose.onNodeWithText("E-mail").performTextInput("nowy@songly.local")
		compose.onNodeWithText("Hasło").performTextInput("haslo1234")
		compose.onNodeWithText("Powtórz hasło").performTextInput("haslo1234")
		compose.onNode(hasText("Załóż konto") and hasClickAction()).performClick()
	}

	@Test
	fun acceptedRegistrationAsksToCheckInboxInsteadOfLoggingIn() {
		var registered = false
		show(backend()) { registered = true }

		fillForm()

		awaitText("Sprawdź skrzynkę")
		compose.onNodeWithText("nowy@songly.local", substring = true).assertExists()
		assertTrue("konto nie istnieje przed potwierdzeniem adresu", !registered)
	}

	@Test
	fun pastedLinkVerifiesAddressAndLogsIn() {
		val backend = backend()
		var registered = false
		show(backend) { registered = true }
		fillForm()
		awaitText("Sprawdź skrzynkę")

		compose.onNodeWithText("Link z maila")
			.performTextInput("http://localhost:3000/verify-email?token=abc_123")
		compose.onNodeWithText("Potwierdź adres").performClick()

		compose.waitUntil(timeoutMillis = 5_000) { registered }
		assertEquals(1, backend.callCount("/api/v1/auth/register/verify"))
	}

	@Test
	fun expiredLinkShowsReadableError() {
		val backend = FakeBackend().apply {
			on("/api/v1/auth/register", HttpStatusCode.Accepted) { "" }
			on("/api/v1/auth/register/verify", HttpStatusCode.BadRequest) {
				"""{"code":"INVALID_VERIFICATION_TOKEN","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/auth/register/verify"}"""
			}
		}
		show(backend)
		fillForm()
		awaitText("Sprawdź skrzynkę")

		compose.onNodeWithText("Link z maila").performTextInput("stary-token")
		compose.onNodeWithText("Potwierdź adres").performClick()

		awaitText("nieprawidłowy albo wygasł")
	}

	@Test
	fun verifyIsDisabledForLinkWithoutToken() {
		show(backend())
		fillForm()
		awaitText("Sprawdź skrzynkę")

		compose.onNodeWithText("Link z maila").performTextInput("http://localhost:3000/verify-email")

		compose.onNodeWithText("Potwierdź adres").assertIsNotEnabled()
	}
}
