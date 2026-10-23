package com.songly.android.comments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Comment
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.plusDistinct
import kotlinx.coroutines.launch

/** Maksymalna długość komentarza — zgodna z walidacją backendu (@Size max 500). */
const val MAX_COMMENT_LENGTH = 500

/** Tyle samo, ile backend oddaje domyślnie na stronę komentarzy. */
private const val PAGE_SIZE = 50

/** Stan ekranu komentarzy. */
data class CommentsUiState(
	val isLoading: Boolean = false,
	val comments: List<Comment> = emptyList(),
	val errorMessage: String? = null,
	val draft: String = "",
	val isSubmitting: Boolean = false,
	val submitError: String? = null,
	val currentUserId: String? = null, // do rozpoznania własnych komentarzy (usuwanie)
	val actionError: String? = null, // błąd usuwania komentarza
	val isLoadingMore: Boolean = false, // doczytywanie kolejnej strony
	val appendErrorMessage: String? = null, // błąd doczytywania (lista zostaje)
	val endReached: Boolean = false, // ostatnia strona wczytana
	// Moderuje społeczność, w której jest post — usuwa też cudze komentarze (backend ADR 0045).
	val canModerate: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && comments.isEmpty()

	fun canDelete(comment: Comment): Boolean = comment.authorId == currentUserId || canModerate

	/** Usunięcie cudzego komentarza to decyzja moderacyjna: autor dostaje powiadomienie. */
	fun isModeration(comment: Comment): Boolean = canModerate && comment.authorId != currentUserId

	val canSubmit: Boolean
		get() = draft.isNotBlank() && draft.length <= MAX_COMMENT_LENGTH && !isSubmitting
}

/**
 * Logika ekranu komentarzy jednego posta. Woła [SonglyApiClient.comments] i
 * [SonglyApiClient.addComment] z modułu `shared`. Po dodaniu komentarza dokłada
 * go lokalnie i sygnalizuje callbackiem [submit] (feed podbije licznik).
 */
class CommentsViewModel(
	private val client: SonglyApiClient,
	private val postId: String,
) : ViewModel() {

	var uiState by mutableStateOf(CommentsUiState(isLoading = true))
		private set

	private var nextPage = 0
	private var loadingMore = false
	private var moderationChecked = false

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val page = client.comments(postId, page = 0, size = PAGE_SIZE)
				// Id usera best-effort — służy tylko do pokazania usuwania własnych komentarzy.
				val userId = uiState.currentUserId ?: runCatching { client.currentUserId() }.getOrNull()
				val canModerate = uiState.canModerate || checkModeration()
				nextPage = page.number + 1
				uiState = uiState.copy(
					isLoading = false,
					comments = page.content,
					currentUserId = userId,
					canModerate = canModerate,
					endReached = page.last,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = e.error?.message ?: "Nie udało się wczytać komentarzy (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	fun onDraftChange(value: String) {
		uiState = uiState.copy(draft = value, submitError = null)
	}

	/**
	 * Czy przeglądający moderuje społeczność, w której jest post. Best-effort jak id
	 * użytkownika: bez odpowiedzi zostaje usuwanie własnych komentarzy, a
	 * uprawnienie i tak sprawdza backend. Po pierwszej odpowiedzi już nie pytamy;
	 * zwykłe posty (bez społeczności) kończą się na jednym zapytaniu o post.
	 */
	private suspend fun checkModeration(): Boolean {
		if (moderationChecked) return false
		return runCatching {
			val communityId = client.post(postId).communityId
			(communityId != null && client.community(communityId).isModerator).also { moderationChecked = true }
		}.getOrDefault(false)
	}

	/**
	 * Usuwa komentarz optymistycznie: znika od razu, a przy błędzie wraca na
	 * miejsce. Własny albo — dla moderatora społeczności — cudzy. Po sukcesie
	 * [onDeleted] pozwala feedowi obniżyć licznik komentarzy.
	 */
	fun delete(comment: Comment, onDeleted: () -> Unit) {
		val snapshot = uiState.comments
		uiState = uiState.copy(comments = uiState.comments.filterNot { it.id == comment.id }, actionError = null)
		viewModelScope.launch {
			try {
				client.deleteComment(postId, comment.id)
				onDeleted()
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					comments = snapshot,
					actionError = e.error?.message ?: "Nie udało się usunąć komentarza (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(comments = snapshot, actionError = "Brak połączenia z serwerem.")
			}
		}
	}

	/**
	 * Dokłada kolejną stronę komentarzy. Pod ożywioną dyskusją 50 pozycji to nie
	 * limit teoretyczny — bez tego reszta wątku była nieosiągalna.
	 */
	fun loadNextPage() {
		if (loadingMore || uiState.endReached || uiState.isLoading || uiState.appendErrorMessage != null) return
		uiState = uiState.copy(isLoadingMore = true)
		loadMore()
	}

	/** Ponawia doczytywanie po błędzie (stopka „Ponów"). */
	fun retryNextPage() {
		if (loadingMore) return
		uiState = uiState.copy(isLoadingMore = true, appendErrorMessage = null)
		loadMore()
	}

	private fun loadMore() {
		loadingMore = true
		viewModelScope.launch {
			try {
				val page = client.comments(postId, page = nextPage, size = PAGE_SIZE)
				nextPage = page.number + 1
				uiState = uiState.copy(
					isLoadingMore = false,
					// Dokładamy do aktualnej listy, nie do zapamiętanej kopii: w międzyczasie
					// mógł dojść własny komentarz albo zniknąć usunięty.
					comments = uiState.comments.plusDistinct(page.content) { it.id },
					endReached = page.last,
					appendErrorMessage = null,
				)
			} catch (e: SonglyApiException) {
				val message = e.error?.message ?: "Nie udało się doczytać komentarzy (HTTP ${e.status})"
				uiState = uiState.copy(isLoadingMore = false, appendErrorMessage = message)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoadingMore = false, appendErrorMessage = "Brak połączenia z serwerem.")
			} finally {
				loadingMore = false
			}
		}
	}

	fun submit(onAdded: () -> Unit) {
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, submitError = null)
		viewModelScope.launch {
			try {
				val comment = client.addComment(postId, uiState.draft.trim())
				uiState = uiState.copy(
					isSubmitting = false,
					draft = "",
					comments = uiState.comments + comment,
				)
				onAdded()
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					submitError = e.error?.message ?: "Nie udało się dodać komentarza (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSubmitting = false, submitError = "Brak połączenia z serwerem.")
			}
		}
	}
}
