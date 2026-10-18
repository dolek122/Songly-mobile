package com.songly.android.notifications

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.shared.model.MailPreferences
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Preferencje poczty (ADR 0020 backendu). `PUT` zastępuje ustawienia w całości,
 * więc najważniejsze jest, żeby zmiana jednego przełącznika nie wysłała
 * domyślnej wartości drugiego — po cichu wyłączyłaby kategorię, której nikt nie
 * ruszał.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MailPreferencesTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private val path = "/api/v1/notifications/mail-preferences"

	private fun backend(putStatus: HttpStatusCode = HttpStatusCode.NoContent) = FakeBackend().apply {
		on(HttpMethod.Get, path) { """{"decisionsAboutMe":true,"moderationQueue":false}""" }
		on(HttpMethod.Put, path, putStatus) {
			if (putStatus.value >= 400) {
				"""{"code":"VALIDATION_ERROR","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"$path"}"""
			} else {
				""
			}
		}
	}

	private fun awaitIdle(viewModel: MailPreferencesViewModel) {
		val deadline = System.currentTimeMillis() + 5_000
		while ((viewModel.uiState.isLoading || viewModel.uiState.isSaving) && System.currentTimeMillis() < deadline) {
			Thread.sleep(20)
		}
	}

	@Test
	fun toggleSendsBothFieldsKeepingTheUntouchedOne() {
		val backend = backend()
		val viewModel = MailPreferencesViewModel(backend.client())
		viewModel.load()
		awaitIdle(viewModel)

		viewModel.onModerationQueueChange(true)
		awaitIdle(viewModel)

		assertEquals(
			"""{"decisionsAboutMe":true,"moderationQueue":true}""",
			backend.lastBody(HttpMethod.Put, path),
		)
		assertEquals(MailPreferences(decisionsAboutMe = true, moderationQueue = true), viewModel.uiState.preferences)
	}

	@Test
	fun rejectedSaveRestoresPreviousState() {
		val viewModel = MailPreferencesViewModel(backend(HttpStatusCode.BadRequest).client())
		viewModel.load()
		awaitIdle(viewModel)

		viewModel.onDecisionsAboutMeChange(false)
		awaitIdle(viewModel)

		// UI nie może twierdzić, że poczta jest wyłączona, skoro serwer dalej ją wyśle.
		assertEquals(MailPreferences(decisionsAboutMe = true, moderationQueue = false), viewModel.uiState.preferences)
		assertNotNull(viewModel.uiState.statusMessage)
	}

	@Test
	fun togglesAreIgnoredBeforePreferencesLoad() {
		val backend = backend()
		val viewModel = MailPreferencesViewModel(backend.client())

		// Bez wczytanego stanu nie znamy wartości drugiego pola — zapis by ją zgadywał.
		viewModel.onModerationQueueChange(true)

		assertEquals(0, backend.callCount(HttpMethod.Put, path))
		assertFalse(viewModel.uiState.isSaving)
	}
}
