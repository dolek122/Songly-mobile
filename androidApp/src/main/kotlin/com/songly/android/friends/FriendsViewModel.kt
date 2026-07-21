package com.songly.android.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendRequest
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.UserSearchResult
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Stan ekranu znajomych: lista znajomych + zaproszenia otrzymane i wysłane + wyszukiwarka. */
data class FriendsUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val friends: List<Friend> = emptyList(),
	val incoming: List<FriendRequest> = emptyList(),
	val outgoing: List<FriendRequest> = emptyList(),
	val searchQuery: String = "",
	val isSearching: Boolean = false,
	val searchResults: List<UserSearchResult> = emptyList(),
	val searched: Boolean = false, // czy było już wyszukiwanie (do rozróżnienia „brak wyników")
	val isBusy: Boolean = false, // akcja mutująca w toku (blokuje przyciski)
	val statusMessage: String? = null,
) {
	val canSearch: Boolean
		get() = searchQuery.isNotBlank() && !isSearching
}

/**
 * Logika ekranu znajomych (moduł social-graph backendu przez `shared`). Po każdej
 * mutacji (wysłanie/akceptacja/odrzucenie/anulowanie/usunięcie) przeładowuje
 * wszystkie trzy listy — prostsze i pewniejsze niż ręczne przenoszenie pozycji
 * między listami (np. akceptacja usuwa z „otrzymanych" i dodaje do „znajomych").
 *
 * Uwaga: backend nie ma wyszukiwarki użytkowników, więc zaproszenie wysyła się po
 * ID użytkownika (UUID). Wyszukiwanie to osobna funkcja do dołożenia w backendzie.
 */
class FriendsViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FriendsUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				uiState = uiState.copy(
					isLoading = false,
					friends = client.friends().content,
					incoming = client.friendRequests(direction = "INCOMING").content,
					outgoing = client.friendRequests(direction = "OUTGOING").content,
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = e.error?.message ?: "Nie udało się wczytać znajomych (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	fun onSearchQueryChange(value: String) {
		uiState = uiState.copy(searchQuery = value, statusMessage = null)
	}

	/** Wyszukuje użytkowników po nazwie. Osobny stan [FriendsUiState.isSearching]. */
	fun search() {
		if (!uiState.canSearch) return
		uiState = uiState.copy(isSearching = true, statusMessage = null)
		viewModelScope.launch {
			try {
				val results = client.searchUsers(uiState.searchQuery.trim()).content
				uiState = uiState.copy(isSearching = false, searchResults = results, searched = true)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSearching = false,
					statusMessage = e.error?.message ?: "Nie udało się wyszukać (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSearching = false, statusMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	/** Wysyła zaproszenie do wybranego z wyników wyszukiwania użytkownika. */
	fun sendRequestTo(userId: String) {
		runAction("Wysłano zaproszenie", "wysłać zaproszenia") {
			client.sendFriendRequest(userId)
			// Usuń zaproszonego z wyników — od razu wędruje do „wysłanych".
			uiState = uiState.copy(searchResults = uiState.searchResults.filterNot { it.userId == userId })
		}
	}

	fun accept(requestId: String) =
		runAction("Zaakceptowano", "zaakceptować zaproszenia") { client.acceptFriendRequest(requestId) }

	fun decline(requestId: String) =
		runAction("Odrzucono", "odrzucić zaproszenia") { client.declineFriendRequest(requestId) }

	fun cancel(requestId: String) =
		runAction("Anulowano", "anulować zaproszenia") { client.cancelFriendRequest(requestId) }

	fun removeFriend(userId: String) =
		runAction("Usunięto znajomego", "usunąć znajomego") { client.removeFriend(userId) }

	/**
	 * Wykonuje akcję mutującą, potem przeładowuje listy. [isBusy] blokuje
	 * równoległe akcje; komunikat sukcesu/błędu ląduje w [FriendsUiState.statusMessage].
	 */
	private fun runAction(successMessage: String, actionLabel: String, action: suspend () -> Unit) {
		if (uiState.isBusy) return
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				action()
				reloadLists()
				uiState = uiState.copy(isBusy = false, statusMessage = successMessage)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isBusy = false,
					statusMessage = e.error?.message ?: "Nie udało się $actionLabel (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isBusy = false, statusMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	private suspend fun reloadLists() {
		uiState = uiState.copy(
			friends = client.friends().content,
			incoming = client.friendRequests(direction = "INCOMING").content,
			outgoing = client.friendRequests(direction = "OUTGOING").content,
		)
	}
}
