package com.songly.android.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendRequest
import com.songly.shared.model.Page
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.UserSearchResult
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.PagedSection
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Stan ekranu znajomych: lista znajomych + zaproszenia otrzymane i wysłane + wyszukiwarka. */
data class FriendsUiState(
	val isLoading: Boolean = true,
	val isRefreshing: Boolean = false, // odświeżanie gestem (listy zostają na ekranie)
	val errorMessage: String? = null,
	val friends: PagedSection<Friend> = PagedSection(),
	val incoming: PagedSection<FriendRequest> = PagedSection(),
	val outgoing: PagedSection<FriendRequest> = PagedSection(),
	val blocked: PagedSection<BlockedUser> = PagedSection(),
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
				// Najpierw dane, potem zgaszenie spinnera — odwrotna kolejność mrugałaby
				// pustym ekranem przez czas trwania czterech żądań.
				reloadLists()
				uiState = uiState.copy(isLoading = false)
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

	/**
	 * Odświeżenie gestem: te same cztery żądania co przy wejściu, ale bez chowania
	 * list za spinnerem — przy pull-to-refresh użytkownik ma widzieć zawartość,
	 * którą właśnie ściąga w dół. Doczytane wcześniej strony wracają do pierwszej,
	 * tak samo jak po każdej mutacji.
	 */
	fun pullToRefresh() {
		if (uiState.isRefreshing) return
		uiState = uiState.copy(isRefreshing = true, errorMessage = null)
		viewModelScope.launch {
			try {
				reloadLists()
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					statusMessage = e.error?.message ?: "Nie udało się odświeżyć (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(statusMessage = "Brak połączenia z serwerem.")
			} finally {
				uiState = uiState.copy(isRefreshing = false)
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
			dropSearchResult(userId)
		}
	}

	/**
	 * Gdy z wyników zniknie ostatnia osoba, chowamy też „brak użytkowników dla tej
	 * frazy" — ta osoba istnieje, tylko przeszła do innej sekcji.
	 */
	private fun dropSearchResult(userId: String) {
		val remaining = uiState.searchResults.filterNot { it.userId == userId }
		uiState = uiState.copy(searchResults = remaining, searched = uiState.searched && remaining.isNotEmpty())
	}

	fun accept(requestId: String) =
		runAction("Zaakceptowano", "zaakceptować zaproszenia") { client.acceptFriendRequest(requestId) }

	fun decline(requestId: String) =
		runAction("Odrzucono", "odrzucić zaproszenia") { client.declineFriendRequest(requestId) }

	fun cancel(requestId: String) =
		runAction("Anulowano", "anulować zaproszenia") { client.cancelFriendRequest(requestId) }

	fun removeFriend(userId: String) =
		runAction("Usunięto znajomego", "usunąć znajomego") { client.removeFriend(userId) }

	/** Blokuje użytkownika (usuwa też znajomość i zaproszenia — stąd przeładowanie list). */
	fun block(userId: String) =
		runAction("Zablokowano", "zablokować użytkownika") {
			client.blockUser(userId)
			// Zablokowany znika z ewentualnych wyników wyszukiwania.
			dropSearchResult(userId)
		}

	fun unblock(userId: String) =
		runAction("Odblokowano", "odblokować użytkownika") { client.unblockUser(userId) }

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

	fun loadMoreFriends() = loadMoreSection({ uiState.friends }, { uiState = uiState.copy(friends = it) }) { page ->
		client.friends(page, PAGE_SIZE)
	}

	fun retryMoreFriends() =
		loadMoreSection({ uiState.friends }, { uiState = uiState.copy(friends = it) }, retry = true) { page ->
			client.friends(page, PAGE_SIZE)
		}

	fun loadMoreIncoming() = loadMoreSection({ uiState.incoming }, { uiState = uiState.copy(incoming = it) }) { page ->
		client.friendRequests("INCOMING", page, PAGE_SIZE)
	}

	fun retryMoreIncoming() =
		loadMoreSection({ uiState.incoming }, { uiState = uiState.copy(incoming = it) }, retry = true) { page ->
			client.friendRequests("INCOMING", page, PAGE_SIZE)
		}

	fun loadMoreOutgoing() = loadMoreSection({ uiState.outgoing }, { uiState = uiState.copy(outgoing = it) }) { page ->
		client.friendRequests("OUTGOING", page, PAGE_SIZE)
	}

	fun retryMoreOutgoing() =
		loadMoreSection({ uiState.outgoing }, { uiState = uiState.copy(outgoing = it) }, retry = true) { page ->
			client.friendRequests("OUTGOING", page, PAGE_SIZE)
		}

	fun loadMoreBlocked() = loadMoreSection({ uiState.blocked }, { uiState = uiState.copy(blocked = it) }) { page ->
		client.blockedUsers(page, PAGE_SIZE)
	}

	fun retryMoreBlocked() =
		loadMoreSection({ uiState.blocked }, { uiState = uiState.copy(blocked = it) }, retry = true) { page ->
			client.blockedUsers(page, PAGE_SIZE)
		}

	/**
	 * Doczytywanie jednej z czterech list. Getter i setter zamiast identyfikatora
	 * sekcji, bo listy trzymają różne typy — inaczej trzeba by czterech kopii tej
	 * samej obsługi błędów i blokad.
	 *
	 * Stan czytamy przez [get] również po powrocie z sieci: w międzyczasie mogła
	 * przejść mutacja (akceptacja zaproszenia przeładowuje listy), więc dokładamy do
	 * tego, co jest teraz, a nie do zapamiętanej kopii.
	 */
	private fun <T> loadMoreSection(
		get: () -> PagedSection<T>,
		set: (PagedSection<T>) -> Unit,
		retry: Boolean = false,
		fetch: suspend (page: Int) -> Page<T>,
	) {
		val current = get()
		// Ponowienie startuje wprost z błędu (canLoadMore jest wtedy false), zwykłe
		// doczytywanie tylko wtedy, gdy jest co i kiedy dobierać.
		if (if (retry) current.isLoadingMore else !current.canLoadMore) return
		set(if (retry) current.retrying() else current.loadingMore())
		viewModelScope.launch {
			try {
				set(get().append(fetch(get().nextPage)))
			} catch (e: SonglyApiException) {
				set(get().failed(e.error?.message ?: "Nie udało się doczytać (HTTP ${e.status})"))
			} catch (e: Exception) {
				set(get().failed("Brak połączenia z serwerem."))
			}
		}
	}

	/**
	 * Po mutacji ładujemy każdą listę od pierwszej strony. Doczytane wcześniej
	 * strony przepadają celowo: po akceptacji zaproszenia pozycje przeskakują
	 * między listami, więc sklejanie starej zawartości z nową dałoby duplikaty
	 * albo dziury.
	 */
	private suspend fun reloadLists() {
		uiState = uiState.copy(
			friends = uiState.friends.replaceWith(client.friends(size = PAGE_SIZE)),
			incoming = uiState.incoming.replaceWith(client.friendRequests("INCOMING", size = PAGE_SIZE)),
			outgoing = uiState.outgoing.replaceWith(client.friendRequests("OUTGOING", size = PAGE_SIZE)),
			blocked = uiState.blocked.replaceWith(client.blockedUsers(size = PAGE_SIZE)),
		)
	}
}
