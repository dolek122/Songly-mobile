package com.songly.android.comments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Comment
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Maksymalna długość komentarza — zgodna z walidacją backendu (@Size max 500). */
const val MAX_COMMENT_LENGTH = 500

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
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && comments.isEmpty()

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

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val page = client.comments(postId)
				// Id usera best-effort — służy tylko do pokazania usuwania własnych komentarzy.
				val userId = uiState.currentUserId ?: runCatching { client.currentUserId() }.getOrNull()
				uiState = uiState.copy(isLoading = false, comments = page.content, currentUserId = userId)
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
	 * Usuwa własny komentarz optymistycznie: znika od razu, a przy błędzie wraca na
	 * miejsce. Po sukcesie [onDeleted] pozwala feedowi obniżyć licznik komentarzy.
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
