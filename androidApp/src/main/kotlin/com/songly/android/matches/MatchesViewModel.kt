package com.songly.android.matches

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Match
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Stan ekranu dopasowań (paginacja) + mapa nazw gatunków + zaproszone osoby. */
data class MatchesUiState(
	val isLoading: Boolean = true,
	val isLoadingMore: Boolean = false,
	val matches: List<Match> = emptyList(),
	val genreNames: Map<String, String> = emptyMap(), // id gatunku -> nazwa (do etykiet)
	val errorMessage: String? = null,
	val appendErrorMessage: String? = null,
	val endReached: Boolean = false,
	val invitedIds: Set<String> = emptySet(), // komu wysłano już zaproszenie w tej sesji
	val isBusy: Boolean = false, // wysyłanie zaproszenia w toku
	val statusMessage: String? = null,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && matches.isEmpty()
}

/**
 * Logika ekranu dopasowań (moduł matching przez `shared`). Lista paginowana,
 * malejąco po score (ranking robi backend). Nazwy wspólnych gatunków dokładamy
 * lokalnie z listy wszystkich gatunków (odpowiedź zwraca tylko id). Z poziomu
 * dopasowania można od razu wysłać zaproszenie do znajomych — domyka pętlę
 * discovery → social.
 */
class MatchesViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(MatchesUiState())
		private set

	private var nextPage = 0
	private var loading = false

	fun refresh() {
		if (loading) return
		nextPage = 0
		uiState = uiState.copy(isLoading = true, errorMessage = null, appendErrorMessage = null, endReached = false)
		loadPage(reset = true)
	}

	fun loadNextPage() {
		if (loading || uiState.endReached || uiState.isLoading || uiState.appendErrorMessage != null) return
		uiState = uiState.copy(isLoadingMore = true)
		loadPage(reset = false)
	}

	fun retryNextPage() {
		if (loading) return
		uiState = uiState.copy(appendErrorMessage = null, isLoadingMore = true)
		loadPage(reset = false)
	}

	/** Nazwy wspólnych gatunków danego dopasowania (te, które udało się zmapować). */
	fun sharedGenreNames(match: Match): List<String> =
		match.sharedGenreIds.mapNotNull { uiState.genreNames[it] }

	/** Wysyła zaproszenie do znajomych do dopasowanej osoby; po sukcesie oznacza jako zaproszoną. */
	fun invite(userId: String) {
		if (uiState.isBusy || userId in uiState.invitedIds) return
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				client.sendFriendRequest(userId)
				uiState = uiState.copy(
					isBusy = false,
					invitedIds = uiState.invitedIds + userId,
					statusMessage = "Wysłano zaproszenie",
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isBusy = false,
					statusMessage = e.error?.message ?: "Nie udało się wysłać zaproszenia (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isBusy = false, statusMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	private fun loadPage(reset: Boolean) {
		loading = true
		viewModelScope.launch {
			try {
				// Nazwy gatunków wystarczy pobrać raz (przy odświeżeniu), best-effort.
				if (reset && uiState.genreNames.isEmpty()) {
					runCatching { client.genres(page = 0, size = 100).content.associate { it.id to it.name } }
						.onSuccess { uiState = uiState.copy(genreNames = it) }
				}
				val page = client.matches(page = nextPage, size = PAGE_SIZE)
				val merged = if (reset) page.content else uiState.matches + page.content
				nextPage = page.number + 1
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					matches = merged,
					endReached = page.last,
					errorMessage = null,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				uiState = failure(reset, e.error?.message ?: "Nie udało się wczytać dopasowań (HTTP ${e.status})")
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.")
			} finally {
				loading = false
			}
		}
	}

	private fun failure(reset: Boolean, message: String): MatchesUiState =
		if (reset) {
			uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		} else {
			uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
		}
}
