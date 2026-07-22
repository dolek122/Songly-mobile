package com.songly.android.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.shared.model.Comment
import com.songly.shared.util.RelativeTime

/**
 * Ekran komentarzy jednego posta (Compose, natywny UI — §14). Stan z
 * [CommentsViewModel]; dane z modułu `shared`. Po dodaniu komentarza woła
 * [onCommentAdded] (feed podbije licznik komentarzy posta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
	viewModel: CommentsViewModel,
	onBack: () -> Unit,
	onCommentAdded: () -> Unit,
	onCommentDeleted: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Komentarze") },
				navigationIcon = {
					TextButton(onClick = onBack) { Text("Wróć") }
				},
			)
		},
		bottomBar = {
			CommentInput(
				draft = state.draft,
				isSubmitting = state.isSubmitting,
				canSubmit = state.canSubmit,
				errorMessage = state.submitError,
				remaining = MAX_COMMENT_LENGTH - state.draft.length,
				onDraftChange = viewModel::onDraftChange,
				onSubmit = { viewModel.submit(onCommentAdded) },
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> Text(
					text = state.errorMessage,
					modifier = Modifier.align(Alignment.Center).padding(24.dp),
				)

				state.isEmpty -> Text(
					text = "Brak komentarzy. Bądź pierwszy!",
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier.align(Alignment.Center).padding(24.dp),
				)

				else -> LazyColumn(
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					if (state.actionError != null) {
						item(key = "action-error") {
							Text(
								text = state.actionError,
								color = MaterialTheme.colorScheme.error,
								style = MaterialTheme.typography.bodySmall,
							)
						}
					}
					items(state.comments, key = { it.id }) { comment ->
						CommentRow(
							comment = comment,
							canDelete = comment.authorId == state.currentUserId,
							onDelete = { viewModel.delete(comment, onCommentDeleted) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CommentRow(comment: Comment, canDelete: Boolean, onDelete: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Avatar(displayName = comment.authorDisplayName, avatarUrl = comment.authorAvatarUrl, size = 36.dp)
		Column(modifier = Modifier.weight(1f)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = comment.authorDisplayName,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
				)
				Text(
					text = RelativeTime.format(comment.createdAt),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.weight(1f).padding(start = 8.dp),
				)
				// Kosz tylko na własnych komentarzach.
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
				text = comment.content,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.padding(top = 2.dp),
			)
		}
	}
}

@Composable
private fun CommentInput(
	draft: String,
	isSubmitting: Boolean,
	canSubmit: Boolean,
	errorMessage: String?,
	remaining: Int,
	onDraftChange: (String) -> Unit,
	onSubmit: () -> Unit,
) {
	Column {
		HorizontalDivider()
		if (errorMessage != null) {
			Text(
				text = errorMessage,
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
			)
		}
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
		) {
			OutlinedTextField(
				value = draft,
				onValueChange = onDraftChange,
				placeholder = { Text("Dodaj komentarz…") },
				enabled = !isSubmitting,
				isError = remaining < 0,
				maxLines = 4,
				modifier = Modifier.weight(1f),
			)
			TextButton(onClick = onSubmit, enabled = canSubmit) {
				if (isSubmitting) {
					CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
				} else {
					Text(text = "Wyślij")
				}
			}
		}
	}
}
