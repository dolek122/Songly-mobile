package com.songly.android.profile

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Zmiana hasła odwołuje na backendzie wszystkie sesje, łącznie z bieżącą (§9).
 * Gdyby aplikacja została po niej „zalogowana", następne żądanie skończyłoby się
 * nieudanym odświeżeniem tokenu i wylogowaniem bez słowa wyjaśnienia.
 *
 * Na poziomie ViewModelu, nie ekranu: formularz siedzi w `AlertDialog`, a
 * Robolectric nie doczekuje się bezczynności okna dialogu z aktywnym polem
 * tekstowym (migający kursor) — test ekranu wisiałby na tym artefakcie.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangePasswordTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private fun backend(passwordStatus: HttpStatusCode, passwordBody: String = "") = FakeBackend().apply {
		on("/api/v1/auth/password", passwordStatus) { passwordBody }
		// 401 przy złym obecnym haśle uruchamia we wtyczce Auth odświeżenie tokenu —
		// prawdziwy backend je przyjmuje, więc atrapa też.
		onJson(
			"/api/v1/auth/refresh",
			"""{"accessToken":"a2","refreshToken":"r2","tokenType":"Bearer","expiresInSeconds":900}""",
		)
	}

	private fun ProfileViewModel.fillAndSubmit() {
		openPasswordDialog()
		onCurrentPasswordChange("stareHaslo1")
		onNewPasswordChange("noweHaslo1")
		onConfirmNewPasswordChange("noweHaslo1")
		changePassword()
	}

	private fun awaitIdle(viewModel: ProfileViewModel) {
		val deadline = System.currentTimeMillis() + 5_000
		while (viewModel.uiState.isChangingPassword && System.currentTimeMillis() < deadline) {
			Thread.sleep(20)
		}
	}

	@Test
	fun successEndsLocalSession() {
		val client = backend(HttpStatusCode.NoContent).client()
		val viewModel = ProfileViewModel(client)

		viewModel.fillAndSubmit()
		awaitIdle(viewModel)

		assertTrue(viewModel.uiState.passwordChanged)
		assertFalse("sesja lokalna musi zniknąć razem z serwerową", runBlocking { client.isLoggedIn() })
	}

	@Test
	fun wrongCurrentPasswordKeepsSessionAndExplains() {
		val client = backend(
			HttpStatusCode.Unauthorized,
			"""{"code":"INVALID_CREDENTIALS","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/auth/password"}""",
		).client()
		val viewModel = ProfileViewModel(client)

		viewModel.fillAndSubmit()
		awaitIdle(viewModel)

		assertEquals("Obecne hasło jest nieprawidłowe.", viewModel.uiState.passwordError)
		assertFalse(viewModel.uiState.passwordChanged)
		assertTrue(runBlocking { client.isLoggedIn() })
	}

	@Test
	fun mismatchedConfirmationBlocksSubmit() {
		val viewModel = ProfileViewModel(backend(HttpStatusCode.NoContent).client())
		viewModel.openPasswordDialog()
		viewModel.onCurrentPasswordChange("stareHaslo1")
		viewModel.onNewPasswordChange("noweHaslo1")
		viewModel.onConfirmNewPasswordChange("inneHaslo1")

		assertFalse(viewModel.uiState.canChangePassword)
	}
}
