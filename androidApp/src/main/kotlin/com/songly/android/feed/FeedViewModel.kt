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

private const val PAGE_SIZE = 20

/** Stan ekranu feedu z paginacją. */
data class FeedUiState(
	val isLoading: Boolean = false, // pierwsze wczytanie / odświeżenie (cała lista)
	val isLoadingMore: Boolean = false, // doczytywanie kolejnej strony
	val posts: List<Post> = emptyList(),
	val errorMessage: String? = null, // błąd pierwszego wczytania (pusta lista)
	val appendErrorMessage: String? = null, // błąd doczytywania (lista zostaje)
	val endReached: Boolean = false, // ostatnia strona wczytana
	// Odświeżanie zwróciło 401 mimo auto-refreshu = refresh token też wygasł.
	val sessionExpired: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && posts.isEmpty()
}

/**
 * Logika ekranu feedu z paginacją (infinite scroll). Woła [SonglyApiClient.feed]
 * z modułu `shared` (access token i refresh po 401 obsługuje warstwa sesji).
 *
 * [refresh] ładuje od strony 0 (odświeżenie), [loadNextPage] dokłada kolejną.
 * Flaga [loading] blokuje równoległe/zduplikowane żądania — infinite scroll
 * potrafi wyzwolić doczytanie wielokrotnie zanim pierwsze wróci.
 */
class FeedViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FeedUiState(isLoading = true))
		private set

	private var nextPage = 0
	private var loading = false

	/** Ładuje feed od początku (strona 0), zastępując dotychczasową listę. */
	fun refresh() {
		if (loading) return
		nextPage = 0
		uiState = uiState.copy(isLoading = true, errorMessage = null, appendErrorMessage = null, endReached = false)
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
	 * Przełącza polubienie posta optymistycznie: najpierw aktualizuje UI, potem
	 * woła serwer; przy błędzie cofa zmianę. Lajk „czuje się" natychmiastowy, a
	 * spójność ze stanem serwera i tak wygrywa (rollback).
	 */
	fun toggleLike(post: Post) {
		val liked = !post.likedByMe
		updatePost(post.id) {
			it.copy(likedByMe = liked, likeCount = it.likeCount + if (liked) 1 else -1)
		}
		viewModelScope.launch {
			try {
				if (liked) client.likePost(post.id) else client.unlikePost(post.id)
			} catch (e: Exception) {
				// Rollback do stanu sprzed kliknięcia.
				updatePost(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
			}
		}
	}

	/** Podbija licznik komentarzy posta po dodaniu komentarza na ekranie komentarzy. */
	fun incrementCommentCount(postId: String) {
		updatePost(postId) { it.copy(commentCount = it.commentCount + 1) }
	}

	private fun updatePost(postId: String, transform: (Post) -> Post) {
		uiState = uiState.copy(
			posts = uiState.posts.map { if (it.id == postId) transform(it) else it },
		)
	}

	private fun loadPage(reset: Boolean) {
		loading = true
		viewModelScope.launch {
			try {
				val page = client.feed(page = nextPage, size = PAGE_SIZE)
				val merged = if (reset) page.content else uiState.posts + page.content
				nextPage = page.number + 1
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					posts = merged,
					endReached = page.last,
					errorMessage = null,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				if (e.status == 401) {
					uiState = uiState.copy(isLoading = false, isLoadingMore = false, sessionExpired = true)
				} else {
					val message = e.error?.message ?: "Nie udało się wczytać feedu (HTTP ${e.status})"
					uiState = failure(reset, message)
				}
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.")
			} finally {
				loading = false
			}
		}
	}

	// Błąd pierwszego wczytania czyści listę (errorMessage); błąd doczytywania
	// zostawia dotychczasowe posty i pokazuje footer (appendErrorMessage).
	private fun failure(reset: Boolean, message: String): FeedUiState =
		if (reset) {
			uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		} else {
			uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
		}
}
