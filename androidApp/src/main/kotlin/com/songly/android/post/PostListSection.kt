package com.songly.android.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.songly.android.ui.DeletePostDialog
import com.songly.android.ui.PostCard

/**
 * Posty z [PostListViewModel] jako fragment cudzej `LazyColumn` — ekrany
 * społeczności i profilu mają nad listą własny nagłówek, więc lista nie może być
 * osobnym przewijanym kontenerem (zagnieżdżone przewijanie w tym samym kierunku
 * Compose odrzuca).
 *
 * Doczytywanie jest przyciskiem, nie automatem przy końcu listy: na tych ekranach
 * są też inne sekcje, więc „koniec listy" nie musi znaczyć „koniec postów".
 */
fun LazyListScope.postListItems(
	viewModel: PostListViewModel,
	emptyText: String,
	onOpenComments: (postId: String) -> Unit,
	onOpenAuthor: (userId: String) -> Unit,
) {
	val state = viewModel.uiState
	when {
		state.isLoading -> item(key = "posts-loading") {
			Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
				CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
			}
		}

		state.errorMessage != null -> item(key = "posts-error") {
			Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
				Text(state.errorMessage, style = MaterialTheme.typography.bodyMedium)
				TextButton(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
			}
		}

		state.isEmpty -> item(key = "posts-empty") {
			Text(
				text = emptyText,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(vertical = 8.dp),
			)
		}

		else -> {
			if (state.actionError != null) {
				item(key = "posts-action-error") {
					Text(state.actionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
			}
			items(state.posts.items, key = { "post-${it.id}" }) { post ->
				PostCard(
					post = post,
					canDelete = post.authorId == state.currentUserId,
					onToggleLike = { viewModel.toggleLike(post) },
					onOpenComments = { onOpenComments(post.id) },
					onToggleShare = { viewModel.toggleShare(post) },
					onDelete = { viewModel.requestDelete(post.id) },
					onOpenAuthor = { onOpenAuthor(post.authorId) },
				)
			}
			val posts = state.posts
			if (!posts.endReached || posts.appendError != null) {
				item(key = "posts-footer") {
					Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
						val appendError = posts.appendError
						when {
							posts.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
							appendError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
								Text(appendError, style = MaterialTheme.typography.bodySmall)
								TextButton(onClick = viewModel::retryLoadMore) { Text("Ponów") }
							}
							else -> TextButton(onClick = viewModel::loadMore) { Text("Pokaż starsze posty") }
						}
					}
				}
			}
		}
	}
}

/** Dialog potwierdzenia usunięcia — poza `LazyColumn`, bo okno nie jest pozycją listy. */
@Composable
fun PostListDialogs(viewModel: PostListViewModel) {
	val state = viewModel.uiState
	if (state.pendingDeletePostId != null) {
		DeletePostDialog(
			isDeleting = state.isDeleting,
			onConfirm = viewModel::confirmDelete,
			onDismiss = viewModel::cancelDelete,
		)
	}
}
