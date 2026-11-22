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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NowPlayingViewModelTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private companion object {
		const val COMPLETE = "/api/v1/now-playing/spotify/connect/complete"
	}

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
	fun noContentShowsEmptyStatusNotError() {
		val backend = backend().apply {
			on("/api/v1/now-playing/me", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = NowPlayingViewModel(backend.client())

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

	@Test
	fun authorizedResultCompletesConnectAndRefreshesStatus() {
		val backend = backend().apply {
			on(HttpMethod.Post, COMPLETE, HttpStatusCode.NoContent) { "" }
		}
		val viewModel = NowPlayingViewModel(backend.client())
		viewModel.load()
		awaitIdle(viewModel)
		val callsBefore = backend.callCount("/api/v1/now-playing/me")

		viewModel.onSpotifyConnectResult(SpotifyConnectResult.Authorized("bilet-123"))
		awaitIdle(viewModel)

		assertEquals(1, backend.callCount(HttpMethod.Post, COMPLETE))
		assertEquals("""{"ticket":"bilet-123"}""", backend.lastBody(HttpMethod.Post, COMPLETE))
		assertEquals(callsBefore + 1, backend.callCount("/api/v1/now-playing/me"))
		assertEquals("Połączono ze Spotify", viewModel.uiState.statusMessage)
	}

	@Test
	fun foreignTicketIsReportedAsNotConnected() {
		val backend = backend().apply {
			on(HttpMethod.Post, COMPLETE, HttpStatusCode.BadRequest) {
				"""{"code":"INVALID_CONNECT_TICKET","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"$COMPLETE"}"""
			}
		}
		val viewModel = NowPlayingViewModel(backend.client())

		viewModel.onSpotifyConnectResult(SpotifyConnectResult.Authorized("cudzy-bilet"))
		awaitIdle(viewModel)

		assertEquals(
			"Nie udało się dokończyć łączenia ze Spotify. Link wygasł albo łączenie zaczęto na innym koncie.",
			viewModel.uiState.statusMessage,
		)
	}

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
