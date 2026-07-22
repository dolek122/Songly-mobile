package com.songly.android.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.shared.model.Post
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/**
 * Ekran feedu z paginacją (Compose, natywny UI — §14). Stan pochodzi z
 * [FeedViewModel]; dane z modułu `shared` (auto-refresh tokenów w tle). Gdy sesja
 * wygaśnie bezpowrotnie, wywołuje [onSessionExpired], żeby wrócić do logowania.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
	viewModel: FeedViewModel,
	client: SonglyApiClient,
	onLoggedOut: () -> Unit,
	onSessionExpired: () -> Unit,
	onCreatePost: () -> Unit,
	onOpenComments: (String) -> Unit,
	onOpenProfile: () -> Unit,
	onOpenFriends: () -> Unit,
	onOpenNowPlaying: () -> Unit,
	onOpenNotifications: () -> Unit,
	onOpenCommunities: () -> Unit,
	onOpenMatches: () -> Unit,
) {
	val state = viewModel.uiState
	val scope = rememberCoroutineScope()
	val listState = rememberLazyListState()
	var menuOpen by remember { mutableStateOf(false) }

	// Odśwież przy każdym wejściu na ekran (także po ponownym logowaniu — ViewModel
	// bywa cache'owany na Activity, więc init{} by nie wystarczył).
	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	LaunchedEffect(state.sessionExpired) {
		if (state.sessionExpired) onSessionExpired()
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
				title = { Text("Songly") },
				actions = {
					IconButton(onClick = { menuOpen = true }) {
						// Kropka na ikonie menu sygnalizuje nieprzeczytane powiadomienia.
						BadgedBox(
							badge = {
								if (state.unreadNotifications > 0) {
									Badge { Text(badgeLabel(state.unreadNotifications)) }
								}
							},
						) {
							Text(text = "⋮", style = MaterialTheme.typography.titleLarge)
						}
					}
					DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
						DropdownMenuItem(
							text = {
								Text(
									if (state.unreadNotifications > 0) {
										"Powiadomienia (${state.unreadNotifications})"
									} else {
										"Powiadomienia"
									},
								)
							},
							onClick = { menuOpen = false; onOpenNotifications() },
						)
						DropdownMenuItem(
							text = { Text("Teraz gram") },
							onClick = { menuOpen = false; onOpenNowPlaying() },
						)
						DropdownMenuItem(
							text = { Text("Znajomi") },
							onClick = { menuOpen = false; onOpenFriends() },
						)
						DropdownMenuItem(
							text = { Text("Społeczności") },
							onClick = { menuOpen = false; onOpenCommunities() },
						)
						DropdownMenuItem(
							text = { Text("Dopasowani") },
							onClick = { menuOpen = false; onOpenMatches() },
						)
						DropdownMenuItem(
							text = { Text("Profil") },
							onClick = { menuOpen = false; onOpenProfile() },
						)
						DropdownMenuItem(
							text = { Text("Wyloguj") },
							onClick = {
								menuOpen = false
								scope.launch {
									client.logout()
									onLoggedOut()
								}
							},
						)
					}
				},
			)
		},
		floatingActionButton = {
			FloatingActionButton(onClick = onCreatePost) {
				Text(text = "+", style = MaterialTheme.typography.headlineMedium)
			}
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading && state.posts.isEmpty() -> {
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
				}

				state.errorMessage != null -> {
					ErrorState(message = state.errorMessage, onRetry = viewModel::refresh)
				}

				state.isEmpty -> {
					EmptyState(onRefresh = viewModel::refresh)
				}

				else -> {
					LazyColumn(
						state = listState,
						contentPadding = PaddingValues(16.dp),
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

/** Etykieta badge'a — powyżej 99 nieprzeczytanych pokazujemy „99+", żeby nie rozpychać ikony. */
private fun badgeLabel(count: Long): String = if (count > 99) "99+" else count.toString()

@Composable
private fun DeletePostDialog(isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Usunąć post?") },
		text = { Text("Tej operacji nie można cofnąć.") },
		confirmButton = {
			TextButton(
				onClick = onConfirm,
				enabled = !isDeleting,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) {
				Text(if (isDeleting) "Usuwanie…" else "Usuń")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Anuluj") }
		},
	)
}

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

@Composable
private fun PostCard(
	post: Post,
	canDelete: Boolean,
	onToggleLike: () -> Unit,
	onOpenComments: () -> Unit,
	onToggleShare: () -> Unit,
	onDelete: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = post.authorDisplayName,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.weight(1f),
				)
				// Kosz tylko na własnych postach.
				if (canDelete) {
					Text(
						text = "Usuń",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.clickable(onClick = onDelete),
					)
				}
			}
			Text(
				text = post.content,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 6.dp),
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(20.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(top = 10.dp),
			) {
				// Lajk — klikalny, podświetlony gdy polubiony.
				Text(
					text = "${if (post.likedByMe) "♥" else "♡"} ${post.likeCount}",
					style = MaterialTheme.typography.labelLarge,
					color = if (post.likedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onToggleLike),
				)
				// Komentarze — klikalne, otwierają ekran komentarzy.
				Text(
					text = "💬 ${post.commentCount}",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onOpenComments),
				)
				// Udostępnienie — klikalne, podświetlone gdy udostępnione.
				Text(
					text = "↗ ${post.shareCount}",
					style = MaterialTheme.typography.labelLarge,
					color = if (post.sharedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onToggleShare),
				)
			}
		}
	}
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
	CenteredMessage {
		Text(text = message, style = MaterialTheme.typography.bodyMedium)
		Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
			Text("Spróbuj ponownie")
		}
	}
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
	CenteredMessage {
		Text(text = "Brak postów w feedzie.", style = MaterialTheme.typography.bodyMedium)
		Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) {
			Text("Odśwież")
		}
	}
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		content()
	}
}
