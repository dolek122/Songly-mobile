package com.songly.android.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Stan ekranu feedu. */
data class FeedUiState(
	val isLoading: Boolean = false,
	val posts: List<Post> = emptyList(),
	val errorMessage: String? = null,
	// Odświeżanie zwróciło 401 mimo auto-refreshu = refresh token też wygasł.
	// UI powinno wrócić do logowania.
	val sessionExpired: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && posts.isEmpty()
}

/**
 * Logika ekranu feedu. Woła [SonglyApiClient.feed] z modułu `shared` — access
 * token i ewentualny refresh po 401 obsługuje warstwa sesji, więc tu tylko
 * mapujemy wynik na stan. 401, które przeszło mimo auto-refreshu, oznacza
 * wygasłą sesję: ustawiamy [FeedUiState.sessionExpired].
 */
class FeedViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FeedUiState(isLoading = true))
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val page = client.feed(page = 0, size = 20)
				uiState = uiState.copy(isLoading = false, posts = page.content)
			} catch (e: SonglyApiException) {
				if (e.status == 401) {
					uiState = uiState.copy(isLoading = false, sessionExpired = true)
				} else {
					uiState = uiState.copy(
						isLoading = false,
						errorMessage = e.error?.message ?: "Nie udało się wczytać feedu (HTTP ${e.status})",
					)
				}
			} catch (e: Exception) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.",
				)
			}
		}
	}
}
