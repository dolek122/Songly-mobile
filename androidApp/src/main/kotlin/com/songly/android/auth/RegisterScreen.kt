package com.songly.android.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songly.android.ui.announcedWhenChanged

/**
 * Ekran rejestracji (Compose, natywny UI — §14) w dwóch krokach, jak w backendzie
 * (ADR 0009): formularz → „sprawdź skrzynkę i wklej link". Konto i sesja powstają
 * dopiero po potwierdzeniu adresu; wtedy [RegisterUiState.registered] wyzwala
 * [onRegistered] i trafiamy prosto do zalogowanej aplikacji.
 *
 * Link z maila (`songly://verify-email`) otwiera aplikację i od razu potwierdza
 * adres; wklejenie zostaje dla klientów poczty, które nie podlinkują nieznanego
 * schematu (backend ADR 0037). Lokalnie (backend z `MAIL_PROVIDER=log`) mail ląduje
 * w konsoli backendu.
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

	// Wstecz z kroku weryfikacji wraca do formularza, nie wyrzuca do logowania.
	BackHandler(enabled = state.awaitingVerification && !state.isSubmitting) { viewModel.editEmail() }

	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		SonglyLogo()

		if (state.awaitingVerification) {
			VerificationStep(state, viewModel)
		} else {
			RegistrationForm(state, viewModel)
		}

		if (state.infoMessage != null) {
			Text(
				text = state.infoMessage,
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
			)
		}

		if (state.errorMessage != null) {
			Text(
				text = state.errorMessage,
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.fillMaxWidth().padding(top = 12.dp).announcedWhenChanged(),
			)
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

@Composable
private fun ColumnScope.RegistrationForm(state: RegisterUiState, viewModel: RegisterViewModel) {
	Text(
		text = "Załóż konto",
		style = MaterialTheme.typography.titleMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
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

	SubmitButton(
		label = "Załóż konto",
		busyLabel = "Rejestracja…",
		enabled = state.canSubmit,
		busy = state.isSubmitting,
		onClick = viewModel::submit,
	)
}

@Composable
private fun ColumnScope.VerificationStep(state: RegisterUiState, viewModel: RegisterViewModel) {
	Text(
		text = "Sprawdź skrzynkę",
		style = MaterialTheme.typography.titleLarge,
		modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
	)
	// Wejście z linku w mailu: adresu nie znamy (rejestrację mogło zacząć inne
	// urządzenie), więc nie ma czego wpisać w zdanie ani na co wysłać nowego linku.
	val fromLink = state.email.isBlank()
	Text(
		text = if (fromLink) {
			"Potwierdzamy adres z linku. Jeśli link wygasł, wpisz adres i wyślij nowy."
		} else {
			"Wysłaliśmy link potwierdzający na ${state.email.trim()}. " +
				"Konto powstanie po potwierdzeniu adresu — wklej tutaj link z wiadomości."
		},
		style = MaterialTheme.typography.bodyMedium,
		textAlign = TextAlign.Center,
		modifier = Modifier.padding(bottom = 16.dp),
	)

	OutlinedTextField(
		value = state.verificationInput,
		onValueChange = viewModel::onVerificationInputChange,
		label = { Text("Link z maila") },
		singleLine = true,
		enabled = !state.isSubmitting,
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
		keyboardActions = KeyboardActions(onDone = { viewModel.verify() }),
		modifier = Modifier.fillMaxWidth(),
	)

	SubmitButton(
		label = "Potwierdź adres",
		busyLabel = "Potwierdzanie…",
		enabled = state.canVerify,
		busy = state.isSubmitting,
		onClick = viewModel::verify,
	)

	if (!fromLink) {
		TextButton(onClick = viewModel::resend, enabled = !state.isSubmitting, modifier = Modifier.padding(top = 8.dp)) {
			Text("Wyślij link ponownie")
		}
	}
	TextButton(onClick = viewModel::editEmail, enabled = !state.isSubmitting) {
		Text(if (fromLink) "Wpisz adres i wyślij nowy link" else "Zmień adres e-mail")
	}
}

@Composable
private fun SubmitButton(label: String, busyLabel: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
	Button(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
	) {
		if (busy) {
			CircularProgressIndicator(
				modifier = Modifier.size(18.dp).padding(end = 4.dp),
				strokeWidth = 2.dp,
			)
		}
		Text(if (busy) busyLabel else label)
	}
}
