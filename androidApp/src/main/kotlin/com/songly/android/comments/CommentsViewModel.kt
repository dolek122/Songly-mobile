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

const val MAX_COMMENT_LENGTH = 500

private const val PAGE_SIZE = 50

data class CommentsUiState(
	val isLoading: Boolean = false,
	val comments: List<Comment> = emptyList(),
	val errorMessage: String? = null,
	val draft: String = "",
	val isSubmitting: Boolean = false,
	val submitError: String? = null,
	val currentUserId: String? = null,
	val actionError: String? = null,
	val isLoadingMore: Boolean = false,
	val appendErrorMessage: String? = null,
	val endReached: Boolean = false,
	val canModerate: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && comments.isEmpty()

	fun canDelete(comment: Comment): Boolean = comment.authorId == currentUserId || canModerate

	fun isModeration(comment: Comment): Boolean = canModerate && comment.authorId != currentUserId

	val canSubmit: Boolean
		get() = draft.isNotBlank() && draft.length <= MAX_COMMENT_LENGTH && !isSubmitting
}

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

	private suspend fun checkModeration(): Boolean {
		if (moderationChecked) return false
		return runCatching {
			val communityId = client.post(postId).communityId
			(communityId != null && client.community(communityId).isModerator).also { moderationChecked = true }
		}.getOrDefault(false)
	}

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

	fun loadNextPage() {
		if (loadingMore || uiState.endReached || uiState.isLoading || uiState.appendErrorMessage != null) return
		uiState = uiState.copy(isLoadingMore = true)
		loadMore()
	}

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
