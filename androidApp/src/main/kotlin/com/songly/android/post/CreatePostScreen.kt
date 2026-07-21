package com.songly.android.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Ekran tworzenia posta (Compose, natywny UI — §14). Stan z [CreatePostViewModel];
 * po udanym dodaniu woła [onCreated] (powrót na feed + odświeżenie).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
	viewModel: CreatePostViewModel,
	onCancel: () -> Unit,
	onCreated: () -> Unit,
) {
	val state = viewModel.uiState

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Nowy post") },
				navigationIcon = {
					TextButton(onClick = onCancel, enabled = !state.isSubmitting) {
						Text("Anuluj")
					}
				},
			)
		},
	) { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			OutlinedTextField(
				value = state.content,
				onValueChange = viewModel::onContentChange,
				label = { Text("Co teraz słuchasz?") },
				enabled = !state.isSubmitting,
				isError = state.remaining < 0,
				modifier = Modifier.fillMaxWidth(),
				minLines = 4,
			)

			Text(
				text = "${state.remaining}",
				style = MaterialTheme.typography.labelMedium,
				color = if (state.remaining < 0) {
					MaterialTheme.colorScheme.error
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				textAlign = TextAlign.End,
				modifier = Modifier.fillMaxWidth(),
			)

			if (state.errorMessage != null) {
				Text(
					text = state.errorMessage,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.fillMaxWidth(),
				)
			}

			Button(
				onClick = { viewModel.submit(onCreated) },
				enabled = state.canSubmit,
				modifier = Modifier.align(Alignment.End),
			) {
				if (state.isSubmitting) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp).padding(end = 4.dp),
						strokeWidth = 2.dp,
					)
				}
				Text(if (state.isSubmitting) "Publikowanie…" else "Opublikuj")
			}
		}
	}
}
