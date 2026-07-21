package com.songly.android.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.shared.model.Post
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/**
 * Ekran feedu (Compose, natywny UI — §14). Stan pochodzi z [FeedViewModel];
 * dane z modułu `shared` (auto-refresh tokenów w tle). Gdy sesja wygaśnie
 * bezpowrotnie, wywołuje [onSessionExpired], żeby wrócić do logowania.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
	viewModel: FeedViewModel,
	client: SonglyApiClient,
	onLoggedOut: () -> Unit,
	onSessionExpired: () -> Unit,
	onCreatePost: () -> Unit,
) {
	val state = viewModel.uiState
	val scope = rememberCoroutineScope()

	// Ładuj przy każdym wejściu na ekran (także po ponownym logowaniu — ViewModel
	// bywa cache'owany na Activity, więc init{} by nie wystarczył).
	LaunchedEffect(Unit) {
		viewModel.load()
	}

	LaunchedEffect(state.sessionExpired) {
		if (state.sessionExpired) onSessionExpired()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Songly") },
				actions = {
					TextButton(onClick = {
						scope.launch {
							client.logout()
							onLoggedOut()
						}
					}) {
						Text("Wyloguj")
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
					ErrorState(message = state.errorMessage, onRetry = viewModel::load)
				}

				state.isEmpty -> {
					EmptyState(onRefresh = viewModel::load)
				}

				else -> {
					LazyColumn(
						contentPadding = PaddingValues(16.dp),
						verticalArrangement = Arrangement.spacedBy(12.dp),
						modifier = Modifier.fillMaxSize(),
					) {
						items(state.posts, key = { it.id }) { post ->
							PostCard(post)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun PostCard(post: Post) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = post.authorDisplayName,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
			)
			Text(
				text = post.content,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 6.dp),
			)
			Text(
				text = "♥ ${post.likeCount}   💬 ${post.commentCount}   ↗ ${post.shareCount}",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 10.dp),
			)
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
