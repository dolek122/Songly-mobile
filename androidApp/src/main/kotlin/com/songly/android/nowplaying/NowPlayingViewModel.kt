package com.songly.android.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Stan ekranu „teraz słucham": aktualny status + formularz ręcznego statusu. */
data class NowPlayingUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val status: NowPlayingStatus? = null,
	val title: String = "",
	val artist: String = "",
	val album: String = "",
	val source: String = "",
	val isBusy: Boolean = false,
	val statusMessage: String? = null,
) {
	val canSetManual: Boolean
		get() = title.isNotBlank() && !isBusy
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

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				uiState = uiState.copy(isLoading = false, status = client.nowPlaying())
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = e.error?.message ?: "Nie udało się wczytać statusu (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	fun onTitleChange(v: String) { uiState = uiState.copy(title = v, statusMessage = null) }
	fun onArtistChange(v: String) { uiState = uiState.copy(artist = v, statusMessage = null) }
	fun onAlbumChange(v: String) { uiState = uiState.copy(album = v, statusMessage = null) }
	fun onSourceChange(v: String) { uiState = uiState.copy(source = v, statusMessage = null) }

	/**
	 * Rozpoczyna łączenie ze Spotify i przekazuje URL autoryzacji do otwarcia w
	 * przeglądarce ([onAuthorizeUrl]). Sam OAuth kończy się na callbacku backendu;
	 * po powrocie do aplikacji odśwież [load].
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

	fun disconnectSpotify() =
		runAction("Odłączono Spotify", "odłączyć Spotify") { client.disconnectSpotify() }

	fun setManual() {
		if (!uiState.canSetManual) return
		runAction("Ustawiono status", "ustawić statusu") {
			client.setManualNowPlaying(
				title = uiState.title.trim(),
				artist = uiState.artist.trim().ifBlank { null },
				album = uiState.album.trim().ifBlank { null },
				source = uiState.source.trim().ifBlank { null },
			)
		}
	}

	fun clearManual() =
		runAction("Wyczyszczono status", "wyczyścić statusu") { client.clearManualNowPlaying() }

	private fun runAction(successMessage: String, actionLabel: String, action: suspend () -> Unit) {
		if (uiState.isBusy) return
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				action()
				val fresh = client.nowPlaying()
				uiState = uiState.copy(isBusy = false, status = fresh, statusMessage = successMessage)
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
