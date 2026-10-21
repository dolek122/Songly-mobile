package com.songly.android.profile

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import io.ktor.http.HttpMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Zachęta do ustawienia nazwy przy nazwie nadanej przez backend (ADR 0046):
 * widać ją przy „Użytkownik 123456”, a znika dopiero po zapisaniu własnej nazwy,
 * nie przy samym pisaniu w polu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultDisplayNameTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private fun profile(name: String) =
		"""{"userId":"me-1","displayName":"$name","bio":null,"avatarUrl":null,"createdAt":"2026-01-01T10:00:00Z"}"""

	private fun awaitUntil(condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + 5_000
		while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
	}

	@Test
	fun recognisesOnlyTheBackendDefault() {
		assertTrue(isDefaultDisplayName("Użytkownik 042137"))
		assertFalse(isDefaultDisplayName("Użytkownik 12345"))
		assertFalse(isDefaultDisplayName("Użytkownik 1234567"))
		assertFalse(isDefaultDisplayName("Ola"))
	}

	@Test
	fun hintFollowsTheSavedName_notTheDraft() {
		val backend = FakeBackend().apply {
			on(HttpMethod.Get, "/api/v1/profiles/me") { profile("Użytkownik 042137") }
			on(HttpMethod.Put, "/api/v1/profiles/me") { profile("Ola") }
			onJson("/api/v1/profiles/me/genres", "[]")
			onJson(
				"/api/v1/genres",
				"""{"content":[],"page":{"size":100,"number":0,"totalElements":0,"totalPages":0}}""",
			)
		}
		val viewModel = ProfileViewModel(backend.client())

		viewModel.load()
		awaitUntil { !viewModel.uiState.isLoading }
		assertTrue(viewModel.uiState.hasDefaultName)

		viewModel.onDisplayNameChange("Ola")
		assertTrue(viewModel.uiState.hasDefaultName)

		viewModel.saveProfile()
		awaitUntil { !viewModel.uiState.isSavingProfile }
		assertFalse(viewModel.uiState.hasDefaultName)
	}
}
