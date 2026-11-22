package com.songly.android.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.util.LinkToken
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LENGTH = 8
private const val CONNECTION_ERROR = "Brak połączenia z serwerem. Spróbuj ponownie."

data class RegisterUiState(
	val email: String = "",
	val password: String = "",
	val confirmPassword: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val awaitingVerification: Boolean = false,
	val verificationInput: String = "",
	val infoMessage: String? = null,
	val registered: Boolean = false,
) {
	val passwordTooShort: Boolean
		get() = password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH

	val passwordsMismatch: Boolean
		get() = confirmPassword.isNotEmpty() && confirmPassword != password

	val canSubmit: Boolean
		get() = email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH &&
			confirmPassword == password && !isSubmitting

	val canVerify: Boolean
		get() = LinkToken.extract(verificationInput) != null && !isSubmitting
}

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

	fun onVerificationInputChange(value: String) {
		uiState = uiState.copy(verificationInput = value, errorMessage = null, infoMessage = null)
	}

	fun submit() {
		if (!uiState.canSubmit) return
		sendRegistration { it.copy(awaitingVerification = true) }
	}

	fun resend() {
		if (uiState.isSubmitting) return
		sendRegistration {
			it.copy(infoMessage = "Jeśli od poprzedniego minęła co najmniej minuta, wysłaliśmy nowy link.")
		}
	}

	fun editEmail() {
		uiState = uiState.copy(
			awaitingVerification = false,
			verificationInput = "",
			errorMessage = null,
			infoMessage = null,
		)
	}

	fun openVerificationLink(token: String) {
		uiState = uiState.copy(
			awaitingVerification = true,
			verificationInput = token,
			errorMessage = null,
			infoMessage = null,
		)
		verify()
	}

	fun verify() {
		if (!uiState.canVerify) return
		val token = LinkToken.extract(uiState.verificationInput) ?: return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
		viewModelScope.launch {
			try {
				client.verifyRegistration(token)
				uiState = uiState.copy(isSubmitting = false, registered = true)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = if (e.error?.code == "INVALID_VERIFICATION_TOKEN") {
						"Link jest nieprawidłowy albo wygasł. Wyślij nowy i spróbuj ponownie."
					} else {
						e.error?.message ?: "Nie udało się potwierdzić adresu (HTTP ${e.status})"
					},
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSubmitting = false, errorMessage = CONNECTION_ERROR)
			}
		}
	}

	private fun sendRegistration(onSuccess: (RegisterUiState) -> RegisterUiState) {
		uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
		viewModelScope.launch {
			try {
				client.register(uiState.email.trim(), uiState.password)
				uiState = onSuccess(uiState.copy(isSubmitting = false))
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = e.error?.message ?: "Nie udało się zarejestrować (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSubmitting = false, errorMessage = CONNECTION_ERROR)
			}
		}
	}
}
