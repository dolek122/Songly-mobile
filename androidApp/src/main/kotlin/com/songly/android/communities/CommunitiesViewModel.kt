package com.songly.android.communities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Community
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Która lista społeczności jest pokazywana. */
enum class CommunitiesTab { ALL, MINE }

/** Stan ekranu listy społeczności (paginacja) + dialog tworzenia. */
data class CommunitiesUiState(
	val tab: CommunitiesTab = CommunitiesTab.ALL,
	val isLoading: Boolean = true,
	val isLoadingMore: Boolean = false,
	val isRefreshing: Boolean = false, // odświeżanie gestem (lista zostaje na ekranie)
	val communities: List<Community> = emptyList(),
	val errorMessage: String? = null,
	val appendErrorMessage: String? = null,
	val endReached: Boolean = false,
	val statusMessage: String? = null,
	// Tworzenie nowej społeczności.
	val showCreateDialog: Boolean = false,
	val createName: String = "",
	val createDescription: String = "",
	val isCreating: Boolean = false,
	val createError: String? = null,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && communities.isEmpty()

	// Backend: nazwa @NotBlank, max 100 znaków.
	val canCreate: Boolean
		get() = createName.isNotBlank() && createName.length <= 100 && !isCreating
}

/**
 * Logika ekranu społeczności (moduł community backendu przez `shared`). Lista
 * paginowana jak feed; dołączanie/opuszczanie jest optymistyczne (jak lajki) —
 * aktualizuje `myRole` i licznik członków od razu, a przy błędzie cofa zmianę.
 * Nowa społeczność ląduje na górze listy.
 */
class CommunitiesViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(CommunitiesUiState())
		private set

	private var nextPage = 0
	private var loading = false

	fun refresh() {
		if (loading) return
		nextPage = 0
		uiState = uiState.copy(isLoading = true, errorMessage = null, appendErrorMessage = null, endReached = false)
		loadPage(reset = true)
	}

	/**
	 * Odświeżenie gestem: to samo żądanie co [refresh], ale bez chowania listy za
	 * spinnerem — przy pull-to-refresh użytkownik ma widzieć zawartość, którą
	 * właśnie ściąga w dół.
	 */
	fun pullToRefresh() {
		if (loading) return
		nextPage = 0
		uiState = uiState.copy(isRefreshing = true, errorMessage = null, appendErrorMessage = null, endReached = false)
		loadPage(reset = true)
	}

	/** Przełącza zakładkę (Wszystkie / Moje) i ładuje odpowiednią listę od nowa. */
	fun selectTab(tab: CommunitiesTab) {
		if (tab == uiState.tab || loading) return
		nextPage = 0
		uiState = uiState.copy(
			tab = tab,
			isLoading = true,
			communities = emptyList(),
			errorMessage = null,
			appendErrorMessage = null,
			endReached = false,
			statusMessage = null,
		)
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

	/**
	 * Dołącza/opuszcza optymistycznie; przy błędzie cofa cały stan listy. Na zakładce
	 * „Moje" opuszczona społeczność znika z listy (nie jest już Twoja). Właściciel nie
	 * może opuścić (serwer odrzuci).
	 */
	fun toggleMembership(community: Community) {
		val join = !community.isMember
		val snapshot = uiState.communities
		uiState = uiState.copy(communities = optimisticToggle(snapshot, community.id, join), statusMessage = null)
		viewModelScope.launch {
			try {
				if (join) client.joinCommunity(community.id) else client.leaveCommunity(community.id)
			} catch (e: Exception) {
				// Rollback całej listy do stanu sprzed kliknięcia.
				uiState = uiState.copy(communities = snapshot, statusMessage = membershipError(e, join))
			}
		}
	}

	private fun optimisticToggle(list: List<Community>, communityId: String, join: Boolean): List<Community> {
		// Na „Moje" opuszczenie usuwa wiersz — po opuszczeniu społeczność nie jest już Twoja.
		if (uiState.tab == CommunitiesTab.MINE && !join) {
			return list.filterNot { it.id == communityId }
		}
		return list.map {
			if (it.id != communityId) {
				it
			} else {
				it.copy(
					// Po dołączeniu jesteś MEMBER; po opuszczeniu przestajesz być członkiem.
					myRole = if (join) "MEMBER" else null,
					memberCount = (it.memberCount + if (join) 1 else -1).coerceAtLeast(0),
				)
			}
		}
	}

	fun openCreateDialog() {
		uiState = uiState.copy(showCreateDialog = true, createName = "", createDescription = "", createError = null)
	}

	fun dismissCreateDialog() {
		if (uiState.isCreating) return
		uiState = uiState.copy(showCreateDialog = false, createError = null)
	}

	fun onCreateNameChange(value: String) {
		uiState = uiState.copy(createName = value, createError = null)
	}

	fun onCreateDescriptionChange(value: String) {
		uiState = uiState.copy(createDescription = value, createError = null)
	}

	fun confirmCreate() {
		if (!uiState.canCreate) return
		uiState = uiState.copy(isCreating = true, createError = null)
		viewModelScope.launch {
			try {
				val created = client.createCommunity(
					name = uiState.createName.trim(),
					description = uiState.createDescription.trim().ifBlank { null },
				)
				uiState = uiState.copy(
					isCreating = false,
					showCreateDialog = false,
					communities = listOf(created) + uiState.communities,
					statusMessage = "Utworzono społeczność",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isCreating = false, createError = errorText(e, "utworzyć społeczności"))
			}
		}
	}

	private fun loadPage(reset: Boolean) {
		loading = true
		val tab = uiState.tab
		viewModelScope.launch {
			try {
				val page = if (tab == CommunitiesTab.MINE) {
					client.myCommunities(page = nextPage, size = PAGE_SIZE)
				} else {
					client.communities(page = nextPage, size = PAGE_SIZE)
				}
				val merged = if (reset) page.content else uiState.communities + page.content
				nextPage = page.number + 1
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					communities = merged,
					endReached = page.last,
					errorMessage = null,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				uiState = failure(reset, e.error?.message ?: "Nie udało się wczytać społeczności (HTTP ${e.status})")
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.")
			} finally {
				loading = false
				uiState = uiState.copy(isRefreshing = false)
			}
		}
	}

	private fun failure(reset: Boolean, message: String): CommunitiesUiState =
		if (reset) {
			uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		} else {
			uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
		}

	private fun membershipError(e: Exception, join: Boolean): String =
		errorText(e, if (join) "dołączyć do społeczności" else "opuścić społeczności")

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
