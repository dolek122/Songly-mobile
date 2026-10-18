package com.songly.android.profile

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.profileJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Blokada z ekranu cudzego profilu: potwierdzenie, skutek, cofnięcie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileBlockTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private fun awaitUntil(condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + 5_000
		while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
	}

	private fun backend() = FakeBackend().apply {
		on(HttpMethod.Put, "/api/v1/blocks/u2", HttpStatusCode.NoContent) { "" }
		on(HttpMethod.Delete, "/api/v1/blocks/u2", HttpStatusCode.NoContent) { "" }
	}

	private fun FakeBackend.withProfile() = apply {
		onJson("/api/v1/profiles/me", profileJson("me-1"))
		onJson("/api/v1/profiles/u2", """{"userId":"u2","displayName":"Ola","bio":null,"favoriteGenres":[]}""")
	}

	/** Blokada sprzed wejścia na profil: ekran ma od razu pokazać „Odblokuj" (ADR 0039 backendu). */
	@Test
	fun loadPicksUpExistingBlock() {
		val backend = backend().withProfile().apply {
			on(HttpMethod.Get, "/api/v1/blocks/u2") { """{"blocked":true}""" }
		}
		val viewModel = UserProfileViewModel(backend.client(), "u2")

		viewModel.load()
		awaitUntil { !viewModel.uiState.isLoading }

		assertTrue(viewModel.uiState.blocked)
	}

	/** Stan blokady to dodatek — jego awaria (np. starszy backend) nie zasłania profilu. */
	@Test
	fun failingBlockStatusStillShowsProfile() {
		val backend = backend().withProfile().apply {
			on(HttpMethod.Get, "/api/v1/blocks/u2", HttpStatusCode.MethodNotAllowed) {
				"""{"code":"METHOD_NOT_ALLOWED","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"/p"}"""
			}
		}
		val viewModel = UserProfileViewModel(backend.client(), "u2")

		viewModel.load()
		awaitUntil { !viewModel.uiState.isLoading }

		assertNull(viewModel.uiState.errorMessage)
		assertEquals("Ola", viewModel.uiState.profile?.displayName)
		assertFalse(viewModel.uiState.blocked)
	}

	/** Własny profil nie pyta o blokadę — zablokować siebie i tak się nie da. */
	@Test
	fun ownProfileDoesNotAskAboutBlock() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("u2"))
			onJson("/api/v1/profiles/u2", """{"userId":"u2","displayName":"Ja","bio":null,"favoriteGenres":[]}""")
		}
		val viewModel = UserProfileViewModel(backend.client(), "u2")

		viewModel.load()
		awaitUntil { !viewModel.uiState.isLoading }

		assertTrue(viewModel.uiState.isSelf)
		assertEquals(0, backend.callCount(HttpMethod.Get, "/api/v1/blocks/u2"))
	}

	@Test
	fun blockingClosesDialogMarksBlockedAndRefreshesPosts() {
		val backend = backend()
		val viewModel = UserProfileViewModel(backend.client(), "u2")
		var refreshed = 0

		viewModel.openBlockDialog()
		assertTrue(viewModel.uiState.showBlockDialog)
		viewModel.block(onChanged = { refreshed++ })
		awaitUntil { !viewModel.uiState.isBlocking }

		assertEquals(1, backend.callCount(HttpMethod.Put, "/api/v1/blocks/u2"))
		assertTrue(viewModel.uiState.blocked)
		assertFalse(viewModel.uiState.showBlockDialog)
		// Posty zablokowanego znikają z odpowiedzi backendu — lista musi się odświeżyć.
		assertEquals(1, refreshed)
	}

	/** Zerwana znajomość nie wraca sama — komunikat musi to powiedzieć. */
	@Test
	fun unblockingRestoresInviteAndExplainsLostFriendship() {
		val backend = backend()
		val viewModel = UserProfileViewModel(backend.client(), "u2")
		viewModel.block()
		awaitUntil { viewModel.uiState.blocked }

		viewModel.unblock()
		awaitUntil { !viewModel.uiState.isBlocking && !viewModel.uiState.blocked }

		assertEquals(1, backend.callCount(HttpMethod.Delete, "/api/v1/blocks/u2"))
		assertFalse(viewModel.uiState.blocked)
		assertTrue(viewModel.uiState.blockMessage!!.contains("od nowa"))
	}

	@Test
	fun failedBlockKeepsStateUnblockedAndSaysWhy() {
		val backend = FakeBackend().apply {
			on(HttpMethod.Put, "/api/v1/blocks/u2", HttpStatusCode.NotFound) {
				"""{"code":"USER_NOT_FOUND","message":"Użytkownik nie istnieje","timestamp":"2026-01-01T00:00:00Z","path":"/p"}"""
			}
		}
		val viewModel = UserProfileViewModel(backend.client(), "u2")
		var refreshed = 0

		viewModel.openBlockDialog()
		viewModel.block(onChanged = { refreshed++ })
		awaitUntil { viewModel.uiState.blockMessage != null }

		assertFalse(viewModel.uiState.blocked)
		assertEquals("Użytkownik nie istnieje", viewModel.uiState.blockMessage)
		assertEquals(0, refreshed)
	}
}
