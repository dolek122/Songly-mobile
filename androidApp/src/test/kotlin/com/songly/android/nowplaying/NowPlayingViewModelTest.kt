package com.songly.android.nowplaying

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.shared.util.SpotifyConnectResult
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Konto bez Spotify i bez ręcznego statusu dostaje z `GET /now-playing/me` 404
 * `PROVIDER_NOT_CONNECTED`, nie 204. Traktowane jako błąd zasłaniało cały ekran
 * „Spróbuj ponownie" — razem z formularzem ręcznego statusu i przyciskiem
 * połączenia ze Spotify, czyli jedynymi drogami wyjścia z tego stanu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingViewModelTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private val notConnected =
		"""{"code":"PROVIDER_NOT_CONNECTED","message":"Brak połączonego serwisu muzycznego","timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/now-playing/me"}"""

	private fun backend() = FakeBackend().apply {
		on("/api/v1/now-playing/me", HttpStatusCode.NotFound) { notConnected }
		onJson("/api/v1/now-playing/settings", """{"shareWithFriends":true}""")
		on(HttpMethod.Delete, "/api/v1/now-playing/manual", HttpStatusCode.NoContent) { "" }
	}

	private fun awaitIdle(viewModel: NowPlayingViewModel) {
		val deadline = System.currentTimeMillis() + 5_000
		while ((viewModel.uiState.isLoading || viewModel.uiState.isBusy) && System.currentTimeMillis() < deadline) {
			Thread.sleep(20)
		}
	}

	@Test
	fun accountWithoutProviderShowsEmptyStatusNotError() {
		val viewModel = NowPlayingViewModel(backend().client())

		viewModel.load()
		awaitIdle(viewModel)

		assertFalse(viewModel.uiState.isLoading)
		assertNull(viewModel.uiState.errorMessage)
		assertNull(viewModel.uiState.status)
	}

	@Test
	fun clearingManualStatusReportsSuccess() {
		val viewModel = NowPlayingViewModel(backend().client())
		viewModel.load()
		awaitIdle(viewModel)

		viewModel.clearManual()
		awaitIdle(viewModel)

		assertEquals("Wyczyszczono status", viewModel.uiState.statusMessage)
	}

	/**
	 * Powrót z OAuth (backend ADR 0038): ekran wisiał pod przeglądarką ze starym
	 * statusem, więc wynik musi go odświeżyć, a nie tylko pokazać komunikat.
	 */
	@Test
	fun spotifyConnectResultRefreshesStatusAndExplainsOutcome() {
		val backend = backend()
		val viewModel = NowPlayingViewModel(backend.client())
		viewModel.load()
		awaitIdle(viewModel)
		val callsBefore = backend.callCount("/api/v1/now-playing/me")

		viewModel.onSpotifyConnectResult(SpotifyConnectResult.Connected)
		awaitIdle(viewModel)

		assertEquals(callsBefore + 1, backend.callCount("/api/v1/now-playing/me"))
		assertEquals("Połączono ze Spotify", viewModel.uiState.statusMessage)
	}

	/** Odmowa zgody to decyzja użytkownika, nie awaria — bez słowa o błędzie. */
	@Test
	fun spotifyDenialAndFailureGetDistinctMessages() {
		val viewModel = NowPlayingViewModel(backend().client())

		viewModel.onSpotifyConnectResult(SpotifyConnectResult.Denied)
		awaitIdle(viewModel)
		assertEquals("Anulowano łączenie ze Spotify", viewModel.uiState.statusMessage)

		viewModel.onSpotifyConnectResult(SpotifyConnectResult.Failed("MUSIC_PROVIDER_ERROR"))
		awaitIdle(viewModel)
		assertEquals("Spotify nie odpowiada. Spróbuj ponownie później.", viewModel.uiState.statusMessage)
	}
}
