package com.songly.android.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.plusDistinct
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

data class FeedUiState(
	val isLoading: Boolean = false,
	val isLoadingMore: Boolean = false,
	val isRefreshing: Boolean = false,
	val posts: List<Post> = emptyList(),
	val errorMessage: String? = null,
	val appendErrorMessage: String? = null,
	val snackbarMessage: String? = null,
	val friendsNowPlaying: List<FriendNowPlaying> = emptyList(),
	val scrollToTopPending: Boolean = false,
	val endReached: Boolean = false,
	val unreadNotifications: Long = 0,
	val currentUserId: String? = null,
	val pendingDeletePostId: String? = null,
	val isDeleting: Boolean = false,
	val sessionExpired: Boolean = false,
	val accountSuspended: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && posts.isEmpty()
}

class FeedViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FeedUiState(isLoading = true))
		private set

	private var nextPage = 0
	private var loading = false
	private var loaded = false

	fun onScreenShown() {
		if (loaded) {
			if (!loading) loadPage(reset = true, quiet = true)
		} else {
			refresh()
		}
	}

	fun refresh(scrollToTop: Boolean = false) {
		if (loading) return
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		loadPage(reset = true, scrollToTop = scrollToTop)
	}

	fun pullToRefresh() {
		if (loading) return
		uiState = uiState.copy(isRefreshing = true, errorMessage = null)
		loadPage(reset = true, scrollToTop = true)
	}

	fun scrolledToTop() {
		uiState = uiState.copy(scrollToTopPending = false)
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

	fun toggleLike(post: Post) {
		val liked = !post.likedByMe
		updatePost(post.id) {
			it.copy(likedByMe = liked, likeCount = it.likeCount + if (liked) 1 else -1)
		}
		viewModelScope.launch {
			try {
				if (liked) client.likePost(post.id) else client.unlikePost(post.id)
			} catch (e: Exception) {
				updatePost(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
			}
		}
	}

	fun toggleShare(post: Post) {
		val shared = !post.sharedByMe
		updatePost(post.id) {
			it.copy(sharedByMe = shared, shareCount = it.shareCount + if (shared) 1 else -1)
		}
		viewModelScope.launch {
			try {
				if (shared) client.sharePost(post.id) else client.unsharePost(post.id)
			} catch (e: Exception) {
				updatePost(post.id) { it.copy(sharedByMe = post.sharedByMe, shareCount = post.shareCount) }
			}
		}
	}

	fun incrementCommentCount(postId: String) {
		updatePost(postId) { it.copy(commentCount = it.commentCount + 1) }
	}

	fun decrementCommentCount(postId: String) {
		updatePost(postId) { it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0)) }
	}

	fun snackbarShown() {
		uiState = uiState.copy(snackbarMessage = null)
	}

	fun requestDeletePost(postId: String) {
		uiState = uiState.copy(pendingDeletePostId = postId)
	}

	fun cancelDeletePost() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(pendingDeletePostId = null)
	}

	fun confirmDeletePost() {
		val postId = uiState.pendingDeletePostId ?: return
		if (uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true)
		viewModelScope.launch {
			try {
				client.deletePost(postId)
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					posts = uiState.posts.filterNot { it.id == postId },
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					snackbarMessage = e.error?.message ?: "Nie udało się usunąć posta (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					snackbarMessage = "Brak połączenia z serwerem.",
				)
			}
		}
	}

	private fun updatePost(postId: String, transform: (Post) -> Post) {
		uiState = uiState.copy(
			posts = uiState.posts.map { if (it.id == postId) transform(it) else it },
		)
	}

	private fun loadPage(reset: Boolean, scrollToTop: Boolean = false, quiet: Boolean = false) {
		loading = true
		viewModelScope.launch {
			try {
				val page = client.feed(page = if (reset) 0 else nextPage, size = PAGE_SIZE)
				val keepTail = reset && uiState.posts.size > page.content.size
				val merged = when {
					!reset -> uiState.posts.plusDistinct(page.content) { it.id }
					keepTail -> page.content.plusDistinct(uiState.posts) { it.id }
					else -> page.content
				}
				if (!keepTail) nextPage = page.number + 1
				val unread = if (reset) {
					runCatching { client.unreadNotificationCount() }.getOrDefault(uiState.unreadNotifications)
				} else {
					uiState.unreadNotifications
				}
				val userId = uiState.currentUserId
					?: runCatching { client.currentUserId() }.getOrNull()
				val nowPlaying = if (reset) {
					runCatching { client.friendsNowPlaying().filter { it.status != null } }
						.getOrDefault(uiState.friendsNowPlaying)
				} else {
					uiState.friendsNowPlaying
				}
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					posts = merged,
					endReached = if (keepTail) uiState.endReached else page.last,
					unreadNotifications = unread,
					currentUserId = userId,
					friendsNowPlaying = nowPlaying,
					errorMessage = null,
					appendErrorMessage = if (keepTail) uiState.appendErrorMessage else null,
					accountSuspended = false,
					scrollToTopPending = uiState.scrollToTopPending || scrollToTop,
				)
				loaded = true
			} catch (e: SonglyApiException) {
				if (e.status == 401) {
					uiState = uiState.copy(isLoading = false, isLoadingMore = false, sessionExpired = true)
				} else if (e.error?.code == "ACCOUNT_SUSPENDED") {
					uiState = uiState.copy(isLoading = false, isLoadingMore = false, accountSuspended = true)
				} else {
					val message = e.error?.message ?: "Nie udało się wczytać feedu (HTTP ${e.status})"
					uiState = failure(reset, message, quiet)
				}
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.", quiet)
			} finally {
				loading = false
				uiState = uiState.copy(isRefreshing = false)
			}
		}
	}

	private fun failure(reset: Boolean, message: String, quiet: Boolean): FeedUiState = when {
		reset && uiState.posts.isEmpty() -> uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		reset -> uiState.copy(
			isLoading = false,
			isLoadingMore = false,
			snackbarMessage = if (quiet) uiState.snackbarMessage else message,
		)
		else -> uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
	}
}
