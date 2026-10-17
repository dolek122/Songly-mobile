package com.songly.android.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.songly.shared.model.REPORT_REASONS

/** Dialog zgłoszenia posta. [onReported] po przyjęciu zgłoszenia przez serwer. */
@Composable
fun ReportPostDialog(viewModel: ReportPostViewModel, onReported: () -> Unit, onDismiss: () -> Unit) {
	val state = viewModel.uiState

	LaunchedEffect(state.submitted) {
		if (state.submitted) onReported()
	}

	AlertDialog(
		onDismissRequest = { if (!state.isSubmitting) onDismiss() },
		title = { Text("Zgłoś post") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(
					"Zgłoszenie trafi do moderatorów tej społeczności.",
					style = MaterialTheme.typography.bodySmall,
				)
				REPORT_REASONS.forEach { reason ->
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.fillMaxWidth().selectable(
							selected = state.reason == reason,
							onClick = { viewModel.selectReason(reason) },
							role = Role.RadioButton,
						),
					) {
						RadioButton(selected = state.reason == reason, onClick = null)
						Text(reportReasonLabel(reason))
					}
				}
				OutlinedTextField(
					value = state.details,
					onValueChange = viewModel::onDetailsChange,
					label = { Text("Szczegóły (opcjonalnie)") },
					enabled = !state.isSubmitting,
					isError = state.details.length > 500,
					supportingText = { Text("${state.details.length}/500") },
					minLines = 2,
					modifier = Modifier.fillMaxWidth(),
				)
				if (state.errorMessage != null) {
					Text(state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = viewModel::submit, enabled = state.canSubmit) {
				Text(if (state.isSubmitting) "Wysyłanie…" else "Zgłoś")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !state.isSubmitting) { Text("Anuluj") }
		},
	)
}
