package com.songly.android.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Notification
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Stan ekranu powiadomień z paginacją (infinite scroll) + licznik nieprzeczytanych. */
data class NotificationsUiState(
	val isLoading: Boolean = true, // pierwsze wczytanie / odświeżenie (cała lista)
	val isLoadingMore: Boolean = false, // doczytywanie kolejnej strony
	val isRefreshing: Boolean = false, // odświeżanie gestem (lista zostaje na ekranie)
	val notifications: List<Notification> = emptyList(),
	val unreadCount: Long = 0,
	val errorMessage: String? = null, // błąd pierwszego wczytania (pusta lista)
	val appendErrorMessage: String? = null, // błąd doczytywania (lista zostaje)
	val endReached: Boolean = false, // ostatnia strona wczytana
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && notifications.isEmpty()

	val hasUnread: Boolean
		get() = unreadCount > 0
}

/**
 * Logika ekranu powiadomień (moduł notification backendu przez `shared`). Lista
 * paginowana jak feed ([refresh]/[loadNextPage]); oznaczanie jako przeczytane jest
 * optymistyczne (jak lajki w feedzie) — UI reaguje natychmiast, a przy błędzie
 * serwera cofamy zmianę. [unreadCount] zasila badge i odświeża się przy każdym
 * wejściu na ekran.
 */
class NotificationsViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(NotificationsUiState())
		private set

	private var nextPage = 0
	private var loading = false

	/** Ładuje powiadomienia od początku (strona 0) i odświeża licznik nieprzeczytanych. */
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

	/** Dokłada kolejną stronę na koniec listy. Ignorowane, gdy nie ma czego/kiedy doczytywać. */
	fun loadNextPage() {
		if (loading || uiState.endReached || uiState.isLoading || uiState.appendErrorMessage != null) return
		uiState = uiState.copy(isLoadingMore = true)
		loadPage(reset = false)
	}

	/** Ponawia doczytywanie po błędzie strony (footer „Ponów"). */
	fun retryNextPage() {
		if (loading) return
		uiState = uiState.copy(appendErrorMessage = null, isLoadingMore = true)
		loadPage(reset = false)
	}

	/**
	 * Oznacza pojedyncze powiadomienie jako przeczytane optymistycznie: najpierw UI
	 * (readAt + licznik), potem serwer; przy błędzie cofa zmianę. Ignoruje kliknięcie
	 * w już przeczytane.
	 */
	fun markRead(notification: Notification) {
		if (!notification.isUnread) return
		setReadAt(notification.id, readAt = NOW_PLACEHOLDER)
		uiState = uiState.copy(unreadCount = (uiState.unreadCount - 1).coerceAtLeast(0))
		viewModelScope.launch {
			try {
				client.markNotificationRead(notification.id)
			} catch (e: Exception) {
				// Rollback do stanu sprzed kliknięcia.
				setReadAt(notification.id, readAt = null)
				uiState = uiState.copy(unreadCount = uiState.unreadCount + 1)
			}
		}
	}

	/** Oznacza wszystkie jako przeczytane optymistycznie; przy błędzie przeładowuje listę. */
	fun markAllRead() {
		if (!uiState.hasUnread) return
		val snapshot = uiState
		uiState = uiState.copy(
			notifications = uiState.notifications.map {
				if (it.isUnread) it.copy(readAt = NOW_PLACEHOLDER) else it
			},
			unreadCount = 0,
		)
		viewModelScope.launch {
			try {
				client.markAllNotificationsRead()
			} catch (e: Exception) {
				uiState = snapshot // przywróć nieprzeczytane; refresh doda pewność
				refresh()
			}
		}
	}

	private fun setReadAt(notificationId: String, readAt: String?) {
		uiState = uiState.copy(
			notifications = uiState.notifications.map {
				if (it.id == notificationId) it.copy(readAt = readAt) else it
			},
		)
	}

	private fun loadPage(reset: Boolean) {
		loading = true
		viewModelScope.launch {
			try {
				val page = client.notifications(page = nextPage, size = PAGE_SIZE)
				val merged = if (reset) page.content else uiState.notifications + page.content
				nextPage = page.number + 1
				// Licznik z serwera (autorytatywny) tylko przy odświeżeniu całości.
				val unread = if (reset) runCatching { client.unreadNotificationCount() }.getOrDefault(uiState.unreadCount)
				else uiState.unreadCount
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					notifications = merged,
					unreadCount = unread,
					endReached = page.last,
					errorMessage = null,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				val message = e.error?.message ?: "Nie udało się wczytać powiadomień (HTTP ${e.status})"
				uiState = failure(reset, message)
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.")
			} finally {
				loading = false
				uiState = uiState.copy(isRefreshing = false)
			}
		}
	}

	private fun failure(reset: Boolean, message: String): NotificationsUiState =
		if (reset) {
			uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		} else {
			uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
		}

	private companion object {
		// Znacznik lokalny „przeczytane teraz" — wystarcza fladze isUnread; dokładny
		// czasu i tak przyniesie kolejny refresh z serwera.
		const val NOW_PLACEHOLDER = "now"
	}
}
