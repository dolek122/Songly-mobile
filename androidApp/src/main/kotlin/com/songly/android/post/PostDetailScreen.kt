package com.songly.android.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songly.android.ui.DeletePostDialog
import com.songly.android.ui.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
	viewModel: PostDetailViewModel,
	onBack: () -> Unit,
	onOpenComments: (postId: String) -> Unit,
	onOpenUserProfile: (userId: String) -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}
	LaunchedEffect(state.deleted) {
		if (state.deleted) onBack()
	}

	if (state.showDeleteDialog) {
		DeletePostDialog(
			isDeleting = state.isDeleting,
			onConfirm = viewModel::confirmDelete,
			onDismiss = viewModel::cancelDelete,
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Post") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			val post = state.post
			when {
				state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> Column(
					modifier = Modifier.fillMaxSize().padding(24.dp),
					verticalArrangement = Arrangement.Center,
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					Text(state.errorMessage, textAlign = TextAlign.Center)
					Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				post != null -> Column(
					modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					PostCard(
						post = post,
						canDelete = post.authorId == state.currentUserId,
						onToggleLike = viewModel::toggleLike,
						onOpenComments = { onOpenComments(post.id) },
						onToggleShare = viewModel::toggleShare,
						onDelete = viewModel::requestDelete,
						onOpenAuthor = { onOpenUserProfile(post.authorId) },
					)
					if (state.actionError != null) {
						Text(state.actionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
					}
					TextButton(onClick = { onOpenComments(post.id) }) {
						Text("Pokaż komentarze (${post.commentCount})")
					}
				}
			}
		}
	}
}
