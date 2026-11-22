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

data class NowPlayingUiState(
	val isLoading: Boolean = true,
	val isRefreshing: Boolean = false,
	val errorMessage: String? = null,
	val status: NowPlayingStatus? = null,
	val title: String = "",
	val artist: String = "",
	val album: String = "",
	val source: String = "",
	val isBusy: Boolean = false,
	val statusMessage: String? = null,
	val shareWithFriends: Boolean = true,
	val editingManual: Boolean = false,
) {
	val canSetManual: Boolean
		get() = title.isNotBlank() && !isBusy

	val spotifyConnected: Boolean
		get() = status?.provider == "spotify"
}

class NowPlayingViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(NowPlayingUiState())
		private set

	private var loaded = false

	fun load() {
		uiState = uiState.copy(isLoading = !loaded, errorMessage = null)
		fetch()
	}

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
				uiState = if (loaded) {
					uiState.copy(isLoading = false, isRefreshing = false, statusMessage = message)
				} else {
					uiState.copy(isLoading = false, isRefreshing = false, errorMessage = message)
				}
			}
		}
	}

	fun statusMessageShown() {
		uiState = uiState.copy(statusMessage = null)
	}

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
			uiState = uiState.copy(title = "", artist = "", album = "", source = "")
		}

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
