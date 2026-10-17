package com.songly.android.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Page
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.PagedSection
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

data class PostListUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val posts: PagedSection<Post> = PagedSection(),
	val currentUserId: String? = null,
	val pendingDeletePostId: String? = null,
	val isDeleting: Boolean = false,
	val actionError: String? = null,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && posts.items.isEmpty()
}

/**
 * Stronicowana lista postów z jednego źródła — [loadPage] mówi skąd (społeczność,
 * oś czasu autora). Feed ma własny ViewModel (badge powiadomień, pull-to-refresh);
 * ta klasa obsługuje listy osadzone w innych ekranach, żeby lajk, udostępnienie i
 * usuwanie działały wszędzie tak samo zamiast mieć po kopii w każdym ekranie.
 */
class PostListViewModel(
	private val client: SonglyApiClient,
	private val loadPage: suspend SonglyApiClient.(page: Int, size: Int) -> Page<Post>,
) : ViewModel() {

	var uiState by mutableStateOf(PostListUiState())
		private set

	fun refresh() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val page = client.loadPage(0, PAGE_SIZE)
				val me = runCatching { client.currentUserId() }.getOrNull()
				uiState = uiState.copy(isLoading = false, posts = uiState.posts.replaceWith(page), currentUserId = me)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać postów"))
			}
		}
	}

	fun loadMore() {
		if (!uiState.posts.canLoadMore || uiState.isLoading) return
		fetchNext(uiState.posts.loadingMore())
	}

	fun retryLoadMore() {
		fetchNext(uiState.posts.retrying())
	}

	/** Nowy post (np. właśnie opublikowany w społeczności) na górę listy. */
	fun prepend(post: Post) {
		uiState = uiState.copy(posts = uiState.posts.copy(items = listOf(post) + uiState.posts.items))
	}

	/** Lajk optymistycznie, z rollbackiem przy błędzie — jak w feedzie. */
	fun toggleLike(post: Post) {
		val liked = !post.likedByMe
		update(post.id) { it.copy(likedByMe = liked, likeCount = it.likeCount + if (liked) 1 else -1) }
		viewModelScope.launch {
			try {
				if (liked) client.likePost(post.id) else client.unlikePost(post.id)
			} catch (e: Exception) {
				update(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
			}
		}
	}

	fun toggleShare(post: Post) {
		val shared = !post.sharedByMe
		update(post.id) { it.copy(sharedByMe = shared, shareCount = it.shareCount + if (shared) 1 else -1) }
		viewModelScope.launch {
			try {
				if (shared) client.sharePost(post.id) else client.unsharePost(post.id)
			} catch (e: Exception) {
				update(post.id) { it.copy(sharedByMe = post.sharedByMe, shareCount = post.shareCount) }
			}
		}
	}

	/** Licznik po powrocie z ekranu komentarzy. */
	fun adjustCommentCount(postId: String, delta: Int) {
		update(postId) { it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) }
	}

	fun requestDelete(postId: String) {
		uiState = uiState.copy(pendingDeletePostId = postId, actionError = null)
	}

	fun cancelDelete() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(pendingDeletePostId = null)
	}

	fun confirmDelete() {
		val postId = uiState.pendingDeletePostId ?: return
		if (uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true)
		viewModelScope.launch {
			try {
				client.deletePost(postId)
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					posts = uiState.posts.removeIf { it.id == postId },
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					actionError = errorText(e, "usunąć posta"),
				)
			}
		}
	}

	private fun fetchNext(loading: PagedSection<Post>) {
		uiState = uiState.copy(posts = loading)
		viewModelScope.launch {
			uiState = try {
				uiState.copy(posts = uiState.posts.append(client.loadPage(uiState.posts.nextPage, PAGE_SIZE)))
			} catch (e: Exception) {
				uiState.copy(posts = uiState.posts.failed(errorText(e, "doczytać postów")))
			}
		}
	}

	private fun update(postId: String, transform: (Post) -> Post) {
		uiState = uiState.copy(
			posts = uiState.posts.copy(items = uiState.posts.items.map { if (it.id == postId) transform(it) else it }),
		)
	}
}

internal fun errorText(e: Exception, action: String): String = when (e) {
	is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
	else -> "Brak połączenia z serwerem."
}
