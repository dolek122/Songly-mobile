package com.songly.android.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songly.android.ui.DeletePostDialog
import com.songly.android.ui.EmptyState
import com.songly.android.ui.PostCard
import com.songly.android.ui.SonglyFab

/**
 * Ekran feedu z paginacją (Compose, natywny UI — §14) — pierwsza zakładka dolnego
 * paska. Stan pochodzi z [FeedViewModel]; dane z modułu `shared` (auto-refresh
 * tokenów w tle). Gdy sesja wygaśnie bezpowrotnie, wywołuje [onSessionExpired],
 * żeby wrócić do logowania.
 *
 * W pasku tylko dzwonek powiadomień — reszta aplikacji jest w dolnym pasku, a
 * nie w menu „⋮", w którym trzeba było jej szukać.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
	viewModel: FeedViewModel,
	onSessionExpired: () -> Unit,
	onCreatePost: () -> Unit,
	onOpenComments: (String) -> Unit,
	onOpenUserProfile: (String) -> Unit,
	onOpenNotifications: () -> Unit,
	onAccountSuspended: () -> Unit = {},
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()

	// Odśwież przy każdym wejściu na ekran (także po ponownym logowaniu — ViewModel
	// bywa cache'owany na Activity, więc init{} by nie wystarczył).
	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	LaunchedEffect(state.sessionExpired) {
		if (state.sessionExpired) onSessionExpired()
	}
	LaunchedEffect(state.accountSuspended) {
		if (state.accountSuspended) onAccountSuspended()
	}

	// Infinite scroll: gdy widoczny jest jeden z ostatnich elementów, doczytaj
	// kolejną stronę. Warunki (koniec listy, trwające żądanie, błąd) pilnuje
	// sam [FeedViewModel.loadNextPage].
	val shouldLoadMore by remember {
		derivedStateOf {
			val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			val total = listState.layoutInfo.totalItemsCount
			total > 0 && lastVisible >= total - 3
		}
	}
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) viewModel.loadNextPage()
	}

	if (state.pendingDeletePostId != null) {
		DeletePostDialog(
			isDeleting = state.isDeleting,
			onConfirm = viewModel::confirmDeletePost,
			onDismiss = viewModel::cancelDeletePost,
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { BrandTitle() },
				actions = {
					NotificationsButton(unread = state.unreadNotifications, onClick = onOpenNotifications)
				},
			)
		},
		floatingActionButton = {
			SonglyFab(icon = Icons.Filled.Add, contentDescription = "Nowy post", onClick = onCreatePost)
		},
	) { padding ->
		PullToRefreshBox(
			isRefreshing = state.isRefreshing,
			onRefresh = viewModel::pullToRefresh,
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
			when {
				state.isLoading && state.posts.isEmpty() -> {
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
				}

				state.errorMessage != null -> {
					EmptyState(icon = Icons.Outlined.CloudOff, message = state.errorMessage) {
						Button(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
					}
				}

				state.isEmpty -> {
					EmptyState(icon = Icons.Outlined.LibraryMusic, message = "Brak postów w feedzie.") {
						Button(onClick = viewModel::refresh) { Text("Odśwież") }
					}
				}

				else -> {
					LazyColumn(
						state = listState,
						// Dół z zapasem na przycisk „Nowy post", żeby nie zasłaniał ostatniego posta.
						contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
						modifier = Modifier.fillMaxSize(),
					) {
						items(state.posts, key = { it.id }) { post ->
							PostCard(
								post = post,
								canDelete = post.authorId == state.currentUserId,
								onToggleLike = { viewModel.toggleLike(post) },
								onOpenComments = { onOpenComments(post.id) },
								onToggleShare = { viewModel.toggleShare(post) },
								onDelete = { viewModel.requestDeletePost(post.id) },
								onOpenAuthor = { onOpenUserProfile(post.authorId) },
							)
						}
						if (state.isLoadingMore || state.appendErrorMessage != null) {
							item(key = "footer") {
								FeedFooter(
									isLoading = state.isLoadingMore,
									errorMessage = state.appendErrorMessage,
									onRetry = viewModel::retryNextPage,
								)
							}
						}
					}
				}
			}
		}
	}
}

/** Nazwa aplikacji z zielonym znakiem „korektora" — jedyna ozdoba w pasku. */
@Composable
private fun BrandTitle() {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.semantics { heading() },
	) {
		Icon(
			Icons.Filled.GraphicEq,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(28.dp),
		)
		Text(text = "Songly", style = MaterialTheme.typography.headlineSmall)
	}
}

/**
 * Dzwonek z liczbą nieprzeczytanych. Plakietka jest czysto wizualna, więc opis
 * dla czytnika mówi wprost i „powiadomienia", i ile jest nowych.
 */
@Composable
private fun NotificationsButton(unread: Long, onClick: () -> Unit) {
	val description = if (unread > 0) "Powiadomienia, nieprzeczytane: $unread" else "Powiadomienia"
	IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
		BadgedBox(
			modifier = Modifier.clearAndSetSemantics {},
			badge = {
				if (unread > 0) {
					Badge(
						containerColor = MaterialTheme.colorScheme.primary,
						contentColor = MaterialTheme.colorScheme.onPrimary,
					) {
						Text(badgeLabel(unread))
					}
				}
			},
		) {
			Icon(Icons.Outlined.Notifications, contentDescription = null)
		}
	}
}

/** Etykieta badge'a — powyżej 99 nieprzeczytanych pokazujemy „99+", żeby nie rozpychać ikony. */
private fun badgeLabel(count: Long): String = if (count > 99) "99+" else count.toString()

@Composable
private fun FeedFooter(isLoading: Boolean, errorMessage: String?, onRetry: () -> Unit) {
	Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
		when {
			isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
			errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(text = errorMessage, style = MaterialTheme.typography.bodySmall)
				TextButton(onClick = onRetry) { Text("Ponów") }
			}
		}
	}
}
