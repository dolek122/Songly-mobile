package com.songly.android.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Ekran rejestracji (Compose, natywny UI — §14). Analogiczny do [LoginScreen], z
 * potwierdzeniem hasła i inline'ową walidacją (długość, zgodność haseł). Po udanej
 * rejestracji [RegisterUiState.registered] wyzwala [onRegistered] — sesja jest już
 * założona przez `shared`, więc trafiamy prosto do zalogowanej aplikacji.
 */
@Composable
fun RegisterScreen(
	viewModel: RegisterViewModel,
	onRegistered: () -> Unit,
	onNavigateToLogin: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(state.registered) {
		if (state.registered) onRegistered()
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(text = "Songly", style = MaterialTheme.typography.headlineLarge)
		Text(
			text = "Załóż konto",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
		)

		OutlinedTextField(
			value = state.email,
			onValueChange = viewModel::onEmailChange,
			label = { Text("E-mail") },
			singleLine = true,
			enabled = !state.isSubmitting,
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Email,
				imeAction = ImeAction.Next,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = state.password,
			onValueChange = viewModel::onPasswordChange,
			label = { Text("Hasło") },
			singleLine = true,
			enabled = !state.isSubmitting,
			isError = state.passwordTooShort,
			supportingText = if (state.passwordTooShort) {
				{ Text("Hasło musi mieć co najmniej 8 znaków") }
			} else {
				null
			},
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Password,
				imeAction = ImeAction.Next,
			),
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
		)

		OutlinedTextField(
			value = state.confirmPassword,
			onValueChange = viewModel::onConfirmPasswordChange,
			label = { Text("Powtórz hasło") },
			singleLine = true,
			enabled = !state.isSubmitting,
			isError = state.passwordsMismatch,
			supportingText = if (state.passwordsMismatch) {
				{ Text("Hasła nie są takie same") }
			} else {
				null
			},
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Password,
				imeAction = ImeAction.Done,
			),
			keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
			modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
		)

		if (state.errorMessage != null) {
			Text(
				text = state.errorMessage,
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
			)
		}

		Button(
			onClick = viewModel::submit,
			enabled = state.canSubmit,
			modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
		) {
			if (state.isSubmitting) {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp).padding(end = 4.dp),
					strokeWidth = 2.dp,
				)
			}
			Text(if (state.isSubmitting) "Rejestracja…" else "Załóż konto")
		}

		TextButton(
			onClick = onNavigateToLogin,
			enabled = !state.isSubmitting,
			modifier = Modifier.padding(top = 8.dp),
		) {
			Text("Masz już konto? Zaloguj się")
		}
	}
}
