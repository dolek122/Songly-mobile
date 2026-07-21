package com.songly.android.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Stan ekranu logowania — jedno źródło prawdy dla UI. */
data class LoginUiState(
	val email: String = "",
	val password: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val loggedIn: Boolean = false,
) {
	val canSubmit: Boolean
		get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

/**
 * Logika ekranu logowania. Woła [SonglyApiClient.login] z modułu `shared` i mapuje
 * wynik na [LoginUiState]. Błędy walidacji/uwierzytelnienia z serwera przychodzą
 * jako [SonglyApiException] w spójnym formacie ApiError — pokazujemy `message`;
 * brak połączenia to osobny, przyjazny komunikat.
 */
class LoginViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(LoginUiState())
		private set

	fun onEmailChange(value: String) {
		uiState = uiState.copy(email = value, errorMessage = null)
	}

	fun onPasswordChange(value: String) {
		uiState = uiState.copy(password = value, errorMessage = null)
	}

	fun submit() {
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null)
		viewModelScope.launch {
			try {
				client.login(uiState.email.trim(), uiState.password)
				uiState = uiState.copy(isSubmitting = false, loggedIn = true)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = e.error?.message ?: "Nie udało się zalogować (HTTP ${e.status})",
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
