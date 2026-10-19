package com.songly.android.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songly.android.ui.announcedWhenChanged

/**
 * „Nie pamiętam hasła" (backend ADR 0008). Lokalnie (`MAIL_PROVIDER=log`) link
 * ląduje w konsoli backendu — tak samo jak przy rejestracji wkleja się go tutaj.
 */
@Composable
fun ForgotPasswordScreen(
	viewModel: ForgotPasswordViewModel,
	onPasswordReset: () -> Unit,
	onBack: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(state.passwordReset) {
		if (state.passwordReset) onPasswordReset()
	}
	BackHandler(enabled = state.linkRequested && !state.isSubmitting) { viewModel.backToEmail() }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		SonglyLogo(modifier = Modifier.padding(bottom = 8.dp))
		Text("Reset hasła", style = MaterialTheme.typography.titleLarge)

		if (!state.linkRequested) {
			Text(
				"Podaj adres konta — wyślemy na niego link do ustawienia nowego hasła.",
				style = MaterialTheme.typography.bodyMedium,
				textAlign = TextAlign.Center,
			)
			OutlinedTextField(
				value = state.email,
				onValueChange = viewModel::onEmailChange,
				label = { Text("E-mail") },
				singleLine = true,
				enabled = !state.isSubmitting,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
				modifier = Modifier.fillMaxWidth(),
			)
			Button(onClick = viewModel::requestLink, enabled = state.canRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
				Text(if (state.isSubmitting) "Wysyłanie…" else "Wyślij link")
			}
		} else {
			OutlinedTextField(
				value = state.linkInput,
				onValueChange = viewModel::onLinkChange,
				label = { Text("Link z maila") },
				singleLine = true,
				enabled = !state.isSubmitting,
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = state.newPassword,
				onValueChange = viewModel::onNewPasswordChange,
				label = { Text("Nowe hasło") },
				singleLine = true,
				enabled = !state.isSubmitting,
				isError = state.passwordInvalid,
				supportingText = if (state.passwordInvalid) {
					{ Text("Hasło musi mieć od 8 do 72 znaków") }
				} else {
					null
				},
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = state.confirmPassword,
				onValueChange = viewModel::onConfirmPasswordChange,
				label = { Text("Powtórz nowe hasło") },
				singleLine = true,
				enabled = !state.isSubmitting,
				isError = state.passwordsMismatch,
				supportingText = if (state.passwordsMismatch) {
					{ Text("Hasła nie są takie same") }
				} else {
					null
				},
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
				modifier = Modifier.fillMaxWidth(),
			)
			Button(onClick = viewModel::resetPassword, enabled = state.canReset, modifier = Modifier.fillMaxWidth().height(52.dp)) {
				Text(if (state.isSubmitting) "Zapisywanie…" else "Ustaw nowe hasło")
			}
			TextButton(onClick = viewModel::backToEmail, enabled = !state.isSubmitting) {
				Text("Wyślij link ponownie")
			}
		}

		if (state.infoMessage != null) {
			Text(state.infoMessage, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
		}
		if (state.errorMessage != null) {
			Text(
				state.errorMessage,
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
				textAlign = TextAlign.Center,
				modifier = Modifier.announcedWhenChanged(),
			)
		}

		TextButton(onClick = onBack, enabled = !state.isSubmitting) { Text("Wróć do logowania") }
	}
}
