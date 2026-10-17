package com.songly.android.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * „Nie pamiętam hasła" (backend ADR 0008). Do tej pory mobilka nie miała tej
 * ścieżki wcale, więc zapomniane hasło znaczyło utracone konto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ForgotPasswordScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private val resetPath = "/api/v1/auth/password/reset"

	private fun show(backend: FakeBackend, onPasswordReset: () -> Unit = {}) {
		compose.setContent {
			ForgotPasswordScreen(ForgotPasswordViewModel(backend.client()), onPasswordReset = onPasswordReset, onBack = {})
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun requestLink() {
		compose.onNodeWithText("E-mail").performTextInput("user@songly.local")
		compose.onNodeWithText("Wyślij link").performClick()
		awaitText("Link z maila")
	}

	private fun fillNewPassword(link: String) {
		compose.onNodeWithText("Link z maila").performTextInput(link)
		compose.onNodeWithText("Nowe hasło").performTextInput("noweHaslo1")
		compose.onNodeWithText("Powtórz nowe hasło").performTextInput("noweHaslo1")
		compose.onNodeWithText("Ustaw nowe hasło").performClick()
	}

	/** Backend nie zdradza, czy konto istnieje — komunikat też nie może. */
	@Test
	fun requestNeverClaimsAccountExists() {
		show(FakeBackend().apply { on("$resetPath-request", HttpStatusCode.Accepted) { "" } })

		requestLink()

		awaitText("Jeśli to konto istnieje")
	}

	@Test
	fun pastedLinkSetsNewPassword() {
		val backend = FakeBackend().apply {
			on("$resetPath-request", HttpStatusCode.Accepted) { "" }
			on(resetPath, HttpStatusCode.NoContent) { "" }
		}
		var done = false
		show(backend) { done = true }
		requestLink()

		fillNewPassword("http://localhost:3000/reset-password?token=tok_123")

		compose.waitUntil(timeoutMillis = 5_000) { done }
		val body = backend.lastBody(HttpMethod.Post, resetPath)!!
		assertTrue(body, body.contains("\"token\":\"tok_123\""))
		assertEquals(1, backend.callCount(resetPath))
	}

	@Test
	fun expiredLinkAsksForNewOne() {
		val backend = FakeBackend().apply {
			on("$resetPath-request", HttpStatusCode.Accepted) { "" }
			on(resetPath, HttpStatusCode.BadRequest) {
				"""{"code":"INVALID_PASSWORD_RESET_TOKEN","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"$resetPath"}"""
			}
		}
		show(backend)
		requestLink()

		fillNewPassword("stary")

		awaitText("nieprawidłowy albo wygasł")
	}
}
