package com.songly.android.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

data class PostDetailUiState(
	val isLoading: Boolean = true,
	val post: Post? = null,
	val errorMessage: String? = null,
	val currentUserId: String? = null,
	val showDeleteDialog: Boolean = false,
	val isDeleting: Boolean = false,
	val deleted: Boolean = false,
	val actionError: String? = null,
)

/**
 * Jeden post — cel powiadomień o lajku, komentarzu i udostępnieniu. Wcześniej
 * kliknięcie takiego powiadomienia tylko oznaczało je jako przeczytane, a do
 * samego posta nie było drogi.
 */
class PostDetailViewModel(
	private val client: SonglyApiClient,
	private val postId: String,
) : ViewModel() {

	var uiState by mutableStateOf(PostDetailUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val post = client.post(postId)
				val me = runCatching { client.currentUserId() }.getOrNull()
				uiState = uiState.copy(isLoading = false, post = post, currentUserId = me)
			} catch (e: SonglyApiException) {
				// 404 obejmuje też post autora, który nas zablokował albo został
				// zawieszony — backend celowo tego nie rozróżnia, więc my też nie.
				val message = if (e.status == 404) {
					"Ten post nie jest już dostępny."
				} else {
					errorText(e, "wczytać posta")
				}
				uiState = uiState.copy(isLoading = false, errorMessage = message)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać posta"))
			}
		}
	}

	fun toggleLike() {
		val post = uiState.post ?: return
		val liked = !post.likedByMe
		uiState = uiState.copy(post = post.copy(likedByMe = liked, likeCount = post.likeCount + if (liked) 1 else -1))
		viewModelScope.launch {
			try {
				if (liked) client.likePost(post.id) else client.unlikePost(post.id)
			} catch (e: Exception) {
				uiState = uiState.copy(post = post)
			}
		}
	}

	fun toggleShare() {
		val post = uiState.post ?: return
		val shared = !post.sharedByMe
		uiState = uiState.copy(post = post.copy(sharedByMe = shared, shareCount = post.shareCount + if (shared) 1 else -1))
		viewModelScope.launch {
			try {
				if (shared) client.sharePost(post.id) else client.unsharePost(post.id)
			} catch (e: Exception) {
				uiState = uiState.copy(post = post)
			}
		}
	}

	fun adjustCommentCount(delta: Int) {
		val post = uiState.post ?: return
		uiState = uiState.copy(post = post.copy(commentCount = (post.commentCount + delta).coerceAtLeast(0)))
	}

	fun requestDelete() {
		uiState = uiState.copy(showDeleteDialog = true, actionError = null)
	}

	fun cancelDelete() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(showDeleteDialog = false)
	}

	fun confirmDelete() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true)
		viewModelScope.launch {
			uiState = try {
				client.deletePost(postId)
				uiState.copy(isDeleting = false, showDeleteDialog = false, deleted = true)
			} catch (e: Exception) {
				uiState.copy(isDeleting = false, showDeleteDialog = false, actionError = errorText(e, "usunąć posta"))
			}
		}
	}
}
