package com.songly.android.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.songly.android.ui.formatDateTime

@Composable
fun SuspendedScreen(
	viewModel: SuspendedViewModel,
	onLifted: () -> Unit,
	onSessionEnded: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) { viewModel.load() }
	LaunchedEffect(state.lifted) { if (state.lifted) onLifted() }
	LaunchedEffect(state.sessionEnded) { if (state.sessionEnded) onSessionEnded() }

	val suspension = state.suspension
	when {
		state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}

		state.errorMessage != null || suspension == null -> Column(
			modifier = Modifier.fillMaxSize().padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(state.errorMessage ?: "Nie udało się sprawdzić stanu konta.")
			Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) { Text("Spróbuj ponownie") }
			TextButton(onClick = viewModel::logout) { Text("Wyloguj") }
		}

		else -> Column(
			modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text("Konto zawieszone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
			Text(
				if (suspension.expiresAt == null) {
					"Twoje konto zostało zawieszone bezterminowo przez administrację."
				} else {
					"Twoje konto jest zawieszone do ${formatDateTime(suspension.expiresAt!!)}."
				},
				style = MaterialTheme.typography.bodyLarge,
			)
			if (!suspension.reason.isNullOrBlank()) {
				Text("Powód: ${suspension.reason}", style = MaterialTheme.typography.bodyMedium)
			}

			HorizontalDivider()

			// Jedno odwołanie na zawieszenie; odrzucenie jest ostateczne (ADR 0023).
			when (suspension.appealStatus) {
				null -> {
					OutlinedTextField(
						value = state.appealText,
						onValueChange = viewModel::onAppealTextChange,
						label = { Text("Odwołanie do administracji") },
						enabled = !state.isAppealing,
						minLines = 3,
						isError = state.appealText.length > 1000,
						supportingText = { Text("${state.appealText.length}/1000 · można się odwołać tylko raz") },
						modifier = Modifier.fillMaxWidth(),
					)
					Button(onClick = viewModel::submitAppeal, enabled = state.canAppeal, modifier = Modifier.align(Alignment.End)) {
						Text(if (state.isAppealing) "Wysyłanie…" else "Odwołaj się")
					}
				}

				"PENDING" -> Text("Odwołanie czeka na rozpatrzenie przez administrację.", color = MaterialTheme.colorScheme.primary)
				else -> Text("Administracja odrzuciła odwołanie.", color = MaterialTheme.colorScheme.error)
			}
			if (state.appealError != null) {
				Text(state.appealError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
			}

			HorizontalDivider()

			OutlinedButton(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) { Text("Sprawdź ponownie") }
			OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Wyloguj") }

			Text(
				"Usunięcie konta jest dostępne także w czasie zawieszenia (RODO). Jest nieodwracalne.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			OutlinedTextField(
				value = state.deletePassword,
				onValueChange = viewModel::onDeletePasswordChange,
				label = { Text("Hasło, aby usunąć konto") },
				singleLine = true,
				enabled = !state.isDeleting,
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
				modifier = Modifier.fillMaxWidth(),
			)
			if (state.deleteError != null) {
				Text(state.deleteError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
			}
			TextButton(
				onClick = viewModel::deleteAccount,
				enabled = state.deletePassword.isNotBlank() && !state.isDeleting,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
				modifier = Modifier.align(Alignment.End),
			) {
				Text(if (state.isDeleting) "Usuwanie…" else "Usuń konto")
			}
		}
	}
}
