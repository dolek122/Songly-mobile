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
 * Ekran logowania (Compose, natywny UI — §14). Bezstanowy względem logiki:
 * cały stan i akcje pochodzą z [LoginViewModel]. Po udanym logowaniu
 * [LoginViewModel.LoginUiState.loggedIn] wyzwala [onLoggedIn] (nawigację).
 */
@Composable
fun LoginScreen(
	viewModel: LoginViewModel,
	onLoggedIn: () -> Unit,
	onNavigateToRegister: () -> Unit,
	onForgotPassword: () -> Unit = {},
	/** Komunikat z poprzedniego kroku, np. „hasło zmienione — zaloguj się nowym". */
	notice: String? = null,
) {
	val state = viewModel.uiState

	// Nawigacja to efekt uboczny — poza fazą kompozycji, żeby nie wywołać jej
	// wielokrotnie przy rekompozycji.
	LaunchedEffect(state.loggedIn) {
		if (state.loggedIn) onLoggedIn()
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(text = "Songly", style = MaterialTheme.typography.headlineLarge)
		Text(
			text = "Zaloguj się",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
		)

		if (notice != null) {
			Text(
				text = notice,
				color = MaterialTheme.colorScheme.primary,
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
			)
		}

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
			Text(if (state.isSubmitting) "Logowanie…" else "Zaloguj się")
		}

		TextButton(
			onClick = onNavigateToRegister,
			enabled = !state.isSubmitting,
			modifier = Modifier.padding(top = 8.dp),
		) {
			Text("Nie masz konta? Zarejestruj się")
		}

		TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting) {
			Text("Nie pamiętasz hasła?")
		}
	}
}
