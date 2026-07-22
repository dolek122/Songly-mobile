package com.songly.android.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 8

/** Stan ekranu rejestracji — jedno źródło prawdy dla UI. */
data class RegisterUiState(
	val email: String = "",
	val password: String = "",
	val confirmPassword: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val registered: Boolean = false,
) {
	// Walidacja klienta odwzorowuje reguły backendu (@Email, hasło 8–72 znaki),
	// żeby dać natychmiastowy feedback zamiast czekać na 400 z serwera.
	val passwordTooShort: Boolean
		get() = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH

	val passwordsMismatch: Boolean
		get() = confirmPassword.isNotEmpty() && confirmPassword != password

	val canSubmit: Boolean
		get() = email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH &&
			confirmPassword == password && !isSubmitting
}

/**
 * Logika ekranu rejestracji. Woła [SonglyApiClient.register] z modułu `shared`,
 * które od razu zakłada sesję (zapisuje tokeny) — po sukcesie [registered] wyzwala
 * nawigację jak po logowaniu. Walidacja hasła (długość, zgodność) jest po stronie
 * klienta; resztę (np. zajęty e-mail) zgłasza serwer jako [SonglyApiException].
 */
class RegisterViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(RegisterUiState())
		private set

	fun onEmailChange(value: String) {
		uiState = uiState.copy(email = value, errorMessage = null)
	}

	fun onPasswordChange(value: String) {
		uiState = uiState.copy(password = value, errorMessage = null)
	}

	fun onConfirmPasswordChange(value: String) {
		uiState = uiState.copy(confirmPassword = value, errorMessage = null)
	}

	fun submit() {
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null)
		viewModelScope.launch {
			try {
				client.register(uiState.email.trim(), uiState.password)
				uiState = uiState.copy(isSubmitting = false, registered = true)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = e.error?.message ?: "Nie udało się zarejestrować (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.",
				)
			}
		}
	}
}
