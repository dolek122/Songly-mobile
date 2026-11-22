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

data class FriendsUiState(
	val isLoading: Boolean = true,
	val isRefreshing: Boolean = false,
	val errorMessage: String? = null,
	val friends: PagedSection<Friend> = PagedSection(),
	val incoming: PagedSection<FriendRequest> = PagedSection(),
	val outgoing: PagedSection<FriendRequest> = PagedSection(),
	val blocked: PagedSection<BlockedUser> = PagedSection(),
	val searchQuery: String = "",
	val isSearching: Boolean = false,
	val searchResults: List<UserSearchResult> = emptyList(),
	val searched: Boolean = false,
	val isBusy: Boolean = false,
	val statusMessage: String? = null,
) {
	val canSearch: Boolean
		get() = searchQuery.isNotBlank() && !isSearching
}

class FriendsViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FriendsUiState())
		private set

	private var loaded = false

	fun load() {
		uiState = uiState.copy(isLoading = !loaded, errorMessage = null)
		viewModelScope.launch {
			try {
				reloadLists()
				loaded = true
				uiState = uiState.copy(isLoading = false)
			} catch (e: Exception) {
				val message = when (e) {
					is SonglyApiException -> e.error?.message ?: "Nie udało się wczytać znajomych (HTTP ${e.status})"
					else -> "Brak połączenia z serwerem."
				}
				uiState = if (loaded) {
					uiState.copy(isLoading = false, statusMessage = message)
				} else {
					uiState.copy(isLoading = false, errorMessage = message)
				}
			}
		}
	}

	fun statusMessageShown() {
		uiState = uiState.copy(statusMessage = null)
	}

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

	fun sendRequestTo(userId: String) {
		runAction("Wysłano zaproszenie", "wysłać zaproszenia") {
			client.sendFriendRequest(userId)
			dropSearchResult(userId)
		}
	}

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

	fun block(userId: String) =
		runAction("Zablokowano", "zablokować użytkownika") {
			client.blockUser(userId)
			dropSearchResult(userId)
		}

	fun unblock(userId: String) =
		runAction("Odblokowano", "odblokować użytkownika") { client.unblockUser(userId) }

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

	fun loadMoreFriends() = loadMoreSection({ uiState.friends }, { uiState = uiState.copy(friends = it) }, keyOf = { it.userId }) { page ->
		client.friends(page, PAGE_SIZE)
	}

	fun retryMoreFriends() =
		loadMoreSection({ uiState.friends }, { uiState = uiState.copy(friends = it) }, retry = true, keyOf = { it.userId }) { page ->
			client.friends(page, PAGE_SIZE)
		}

	fun loadMoreIncoming() = loadMoreSection({ uiState.incoming }, { uiState = uiState.copy(incoming = it) }, keyOf = { it.id }) { page ->
		client.friendRequests("INCOMING", page, PAGE_SIZE)
	}

	fun retryMoreIncoming() =
		loadMoreSection({ uiState.incoming }, { uiState = uiState.copy(incoming = it) }, retry = true, keyOf = { it.id }) { page ->
			client.friendRequests("INCOMING", page, PAGE_SIZE)
		}

	fun loadMoreOutgoing() = loadMoreSection({ uiState.outgoing }, { uiState = uiState.copy(outgoing = it) }, keyOf = { it.id }) { page ->
		client.friendRequests("OUTGOING", page, PAGE_SIZE)
	}

	fun retryMoreOutgoing() =
		loadMoreSection({ uiState.outgoing }, { uiState = uiState.copy(outgoing = it) }, retry = true, keyOf = { it.id }) { page ->
			client.friendRequests("OUTGOING", page, PAGE_SIZE)
		}

	fun loadMoreBlocked() = loadMoreSection({ uiState.blocked }, { uiState = uiState.copy(blocked = it) }, keyOf = { it.userId }) { page ->
		client.blockedUsers(page, PAGE_SIZE)
	}

	fun retryMoreBlocked() =
		loadMoreSection({ uiState.blocked }, { uiState = uiState.copy(blocked = it) }, retry = true, keyOf = { it.userId }) { page ->
			client.blockedUsers(page, PAGE_SIZE)
		}

	private fun <T> loadMoreSection(
		get: () -> PagedSection<T>,
		set: (PagedSection<T>) -> Unit,
		retry: Boolean = false,
		keyOf: (T) -> Any?,
		fetch: suspend (page: Int) -> Page<T>,
	) {
		val current = get()
		if (if (retry) current.isLoadingMore else !current.canLoadMore) return
		set(if (retry) current.retrying() else current.loadingMore())
		viewModelScope.launch {
			try {
				set(get().append(fetch(get().nextPage), keyOf))
			} catch (e: SonglyApiException) {
				set(get().failed(e.error?.message ?: "Nie udało się doczytać (HTTP ${e.status})"))
			} catch (e: Exception) {
				set(get().failed("Brak połączenia z serwerem."))
			}
		}
	}

	private suspend fun reloadLists() {
		uiState = uiState.copy(
			friends = uiState.friends.replaceWith(client.friends(size = PAGE_SIZE)),
			incoming = uiState.incoming.replaceWith(client.friendRequests("INCOMING", size = PAGE_SIZE)),
			outgoing = uiState.outgoing.replaceWith(client.friendRequests("OUTGOING", size = PAGE_SIZE)),
			blocked = uiState.blocked.replaceWith(client.blockedUsers(size = PAGE_SIZE)),
		)
	}
}
