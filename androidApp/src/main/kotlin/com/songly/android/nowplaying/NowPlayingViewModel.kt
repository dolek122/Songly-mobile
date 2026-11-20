package com.songly.android.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.util.SpotifyConnectResult
import kotlinx.coroutines.launch

/** Stan ekranu „teraz słucham": aktualny status + formularz ręcznego statusu. */
data class NowPlayingUiState(
	val isLoading: Boolean = true, // pierwsze wczytanie (spinner na cały ekran)
	val isRefreshing: Boolean = false, // odświeżanie gestem (status zostaje na ekranie)
	val errorMessage: String? = null,
	val status: NowPlayingStatus? = null,
	val title: String = "",
	val artist: String = "",
	val album: String = "",
	val source: String = "",
	val isBusy: Boolean = false,
	val statusMessage: String? = null,
	val shareWithFriends: Boolean = true,
	// Formularz ręcznego statusu otwarty (arkusz od dołu) — w stanie, bo zamyka go
	// dopiero udany zapis, który kończy się asynchronicznie.
	val editingManual: Boolean = false,
) {
	val canSetManual: Boolean
		get() = title.isNotBlank() && !isBusy

	/**
	 * Czy Spotify jest podłączone — wiemy to tylko wtedy, gdy właśnie z niego
	 * płynie status (backend nie ma osobnego zapytania o stan połączenia). Bez
	 * statusu pokazujemy więc „Połącz", które przy istniejącym połączeniu po
	 * prostu odnawia zgodę.
	 */
	val spotifyConnected: Boolean
		get() = status?.provider == "spotify"
}

/**
 * Logika ekranu „teraz słucham" (moduł now-playing backendu przez `shared`).
 * Dwie ścieżki: integracja ze Spotify (OAuth — otwierany URL autoryzacji) oraz
 * uniwersalny ręczny status. Po powrocie z przeglądarki Spotify UI woła [load],
 * żeby pobrać świeży status.
 */
class NowPlayingViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(NowPlayingUiState())
		private set

	// Czy status był już choć raz wczytany. Zakładka woła [load] przy każdym wejściu;
	// spinner na cały ekran tylko za pierwszym razem, potem status odświeża się pod
	// spodem, zamiast mrugać przy każdym przełączeniu zakładek.
	private var loaded = false

	fun load() {
		uiState = uiState.copy(isLoading = !loaded, errorMessage = null)
		fetch()
	}

	/** Odświeżenie gestem — to samo co [load], bez chowania ekranu za spinnerem. */
	fun refresh() {
		if (uiState.isRefreshing) return
		uiState = uiState.copy(isRefreshing = true, errorMessage = null)
		fetch()
	}

	private fun fetch() {
		viewModelScope.launch {
			try {
				uiState = uiState.copy(
					isLoading = false,
					isRefreshing = false,
					status = client.nowPlaying(),
					shareWithFriends = client.nowPlayingSettings().shareWithFriends,
				)
				loaded = true
			} catch (e: Exception) {
				val message = errorText(e, "wczytać statusu")
				// Wczytany już status zostaje — chwilowy brak sieci nie zasłania ekranu.
				uiState = if (loaded) {
					uiState.copy(isLoading = false, isRefreshing = false, statusMessage = message)
				} else {
					uiState.copy(isLoading = false, isRefreshing = false, errorMessage = message)
				}
			}
		}
	}

	/** Komunikat pokazany (snackbar) — bez tego wracałby przy każdej rekompozycji. */
	fun statusMessageShown() {
		uiState = uiState.copy(statusMessage = null)
	}

	/**
	 * Otwiera formularz ręcznego statusu. Pusty formularz wypełniamy bieżącym
	 * statusem ręcznym — poprawka literówki w tytule nie powinna wymagać
	 * przepisywania wszystkiego od nowa.
	 */
	fun openManualEditor() {
		val current = uiState.status?.takeIf { it.provider != "spotify" }
		uiState = if (current != null && uiState.title.isBlank()) {
			uiState.copy(
				editingManual = true,
				title = current.title,
				artist = current.artist.orEmpty(),
				album = current.album.orEmpty(),
				source = current.provider.takeIf { it != "manual" }.orEmpty(),
			)
		} else {
			uiState.copy(editingManual = true)
		}
	}

	fun dismissManualEditor() {
		uiState = uiState.copy(editingManual = false)
	}

	fun onTitleChange(v: String) { uiState = uiState.copy(title = v, statusMessage = null) }
	fun onArtistChange(v: String) { uiState = uiState.copy(artist = v, statusMessage = null) }
	fun onAlbumChange(v: String) { uiState = uiState.copy(album = v, statusMessage = null) }
	fun onSourceChange(v: String) { uiState = uiState.copy(source = v, statusMessage = null) }

	/**
	 * Rozpoczyna łączenie ze Spotify i przekazuje URL autoryzacji do otwarcia w
	 * przeglądarce ([onAuthorizeUrl]). Callback backendu odsyła do aplikacji
	 * linkiem — ten trafia do [onSpotifyConnectResult], który łączenie kończy.
	 */
	fun connectSpotify(onAuthorizeUrl: (String) -> Unit) {
		if (uiState.isBusy) return
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				val url = client.connectSpotify()
				uiState = uiState.copy(isBusy = false)
				onAuthorizeUrl(url)
			} catch (e: Exception) {
				uiState = uiState.copy(isBusy = false, statusMessage = errorText(e, "połączyć ze Spotify"))
			}
		}
	}

	/**
	 * Wynik OAuth z linku powrotnego (backend ADR 0038). Status pobieramy na nowo
	 * przy każdym wyniku, nie tylko po sukcesie — ekran mógł wisieć w pamięci od
	 * dawna, a odmowa nie znaczy, że wcześniejsze połączenie zniknęło.
	 *
	 * Zgoda ([SpotifyConnectResult.Authorized]) jeszcze niczego nie łączy (backend
	 * ADR 0050): bilet oddajemy backendowi z naszym tokenem, a ten przypina
	 * Spotify tylko wtedy, gdy łączenie zaczęło to samo konto.
	 */
	fun onSpotifyConnectResult(result: SpotifyConnectResult) {
		when (result) {
			SpotifyConnectResult.Denied -> showConnectOutcome("Anulowano łączenie ze Spotify")
			is SpotifyConnectResult.Failed ->
				showConnectOutcome(connectErrorText(result.code) ?: "Nie udało się połączyć ze Spotify.")
			is SpotifyConnectResult.Authorized -> completeSpotifyConnect(result.ticket)
		}
	}

	private fun completeSpotifyConnect(ticket: String) {
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			val message = try {
				client.completeSpotifyConnect(ticket)
				"Połączono ze Spotify"
			} catch (e: SonglyApiException) {
				connectErrorText(e.error?.code) ?: errorText(e, "połączyć ze Spotify")
			} catch (e: Exception) {
				errorText(e, "połączyć ze Spotify")
			}
			uiState = uiState.copy(isBusy = false)
			showConnectOutcome(message)
		}
	}

	private fun showConnectOutcome(message: String) {
		uiState = uiState.copy(statusMessage = message)
		load()
	}

	/** Kody błędów łączenia — z linku powrotnego i z dokończenia. */
	private fun connectErrorText(code: String?): String? = when (code) {
		"INVALID_CONNECT_TICKET" ->
			"Nie udało się dokończyć łączenia ze Spotify. Link wygasł albo łączenie zaczęto na innym koncie."
		"INVALID_OAUTH_STATE", "INVALID_AUTHORIZATION_CODE" -> "Logowanie do Spotify wygasło. Połącz jeszcze raz."
		"MUSIC_PROVIDER_ERROR" -> "Spotify nie odpowiada. Spróbuj ponownie później."
		else -> null
	}

	fun disconnectSpotify() =
		runAction("Odłączono Spotify", "odłączyć Spotify") { client.disconnectSpotify() }

	fun setManual() {
		if (!uiState.canSetManual) return
		runAction("Ustawiono status", "ustawić statusu", closeEditor = true) {
			client.setManualNowPlaying(
				title = uiState.title.trim(),
				artist = uiState.artist.trim().ifBlank { null },
				album = uiState.album.trim().ifBlank { null },
				source = uiState.source.trim().ifBlank { null },
			)
		}
	}

	fun clearManual() =
		runAction("Wyczyszczono status", "wyczyścić statusu", closeEditor = true) {
			client.clearManualNowPlaying()
			// Wyczyszczony status nie powinien wracać jako podpowiedź przy następnym otwarciu.
			uiState = uiState.copy(title = "", artist = "", album = "", source = "")
		}

	/**
	 * Przełącza widoczność statusu dla znajomych (ADR 0007 backendu, Etap 4).
	 * Przełącznik przestawiamy od razu, żeby nie klikał się z opóźnieniem sieci, i
	 * cofamy, gdy serwer odmówi — inaczej UI kłamałby o stanie prywatności.
	 */
	fun onShareWithFriendsChange(share: Boolean) {
		if (uiState.isBusy) return
		val previous = uiState.shareWithFriends
		uiState = uiState.copy(shareWithFriends = share, isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				client.updateNowPlayingSettings(share)
				uiState = uiState.copy(
					isBusy = false,
					statusMessage = if (share) "Znajomi widzą Twój status" else "Status ukryty przed znajomymi",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isBusy = false,
					shareWithFriends = previous,
					statusMessage = errorText(e, "zmienić widoczności"),
				)
			}
		}
	}

	private fun runAction(
		successMessage: String,
		actionLabel: String,
		closeEditor: Boolean = false,
		action: suspend () -> Unit,
	) {
		if (uiState.isBusy) return
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				action()
				val fresh = client.nowPlaying()
				uiState = uiState.copy(
					isBusy = false,
					status = fresh,
					statusMessage = successMessage,
					editingManual = uiState.editingManual && !closeEditor,
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isBusy = false, statusMessage = errorText(e, actionLabel))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
