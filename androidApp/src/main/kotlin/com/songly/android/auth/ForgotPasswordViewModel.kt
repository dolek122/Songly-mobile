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
private const val MAX_PASSWORD_LENGTH = 72

data class ForgotPasswordUiState(
	val email: String = "",
	/** Link zamówiony — ekran przechodzi w krok „wklej link i ustaw hasło". */
	val linkRequested: Boolean = false,
	val linkInput: String = "",
	val newPassword: String = "",
	val confirmPassword: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val infoMessage: String? = null,
	val passwordReset: Boolean = false,
) {
	val canRequest: Boolean
		get() = email.isNotBlank() && !isSubmitting

	val passwordInvalid: Boolean
		get() = newPassword.isNotEmpty() && newPassword.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH

	val passwordsMismatch: Boolean
		get() = confirmPassword.isNotEmpty() && confirmPassword != newPassword

	val canReset: Boolean
		get() = LinkToken.extract(linkInput) != null &&
			newPassword.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH &&
			confirmPassword == newPassword && !isSubmitting
}

/**
 * Reset hasła (backend ADR 0008) w dwóch krokach, jak rejestracja: zamówienie
 * linku na adres, potem wklejony link + nowe hasło.
 *
 * Krok 1 zawsze kończy się tym samym komunikatem — backend odpowiada 202 także
 * dla adresu bez konta, więc aplikacja nie może twierdzić, że mail wyszedł, ani
 * że nie wyszedł.
 */
class ForgotPasswordViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(ForgotPasswordUiState())
		private set

	fun onEmailChange(value: String) {
		uiState = uiState.copy(email = value, errorMessage = null)
	}

	fun onLinkChange(value: String) {
		uiState = uiState.copy(linkInput = value, errorMessage = null, infoMessage = null)
	}

	fun onNewPasswordChange(value: String) {
		uiState = uiState.copy(newPassword = value, errorMessage = null)
	}

	fun onConfirmPasswordChange(value: String) {
		uiState = uiState.copy(confirmPassword = value, errorMessage = null)
	}

	fun requestLink() {
		if (!uiState.canRequest) return
		run(onSuccess = {
			uiState.copy(
				linkRequested = true,
				infoMessage = "Jeśli to konto istnieje, wysłaliśmy na ten adres link do ustawienia nowego hasła.",
			)
		}) { client.requestPasswordReset(uiState.email.trim()) }
	}

	/**
	 * Wejście z linku w mailu (`songly://reset-password`, ADR 0037): od razu krok
	 * „ustaw nowe hasło" z tokenem w polu. Hasła oczywiście nie zgadniemy, więc tu —
	 * inaczej niż przy weryfikacji adresu — nic nie wysyłamy sami.
	 */
	fun openResetLink(token: String) {
		uiState = uiState.copy(
			linkRequested = true,
			linkInput = token,
			errorMessage = null,
			infoMessage = "Ustaw nowe hasło do swojego konta.",
		)
	}

	/** Wraca do kroku 1 — np. po literówce albo żeby zamówić nowy link. */
	fun backToEmail() {
		uiState = uiState.copy(linkRequested = false, errorMessage = null, infoMessage = null)
	}

	fun resetPassword() {
		if (!uiState.canReset) return
		val token = LinkToken.extract(uiState.linkInput) ?: return
		run(onSuccess = { uiState.copy(passwordReset = true) }) {
			client.resetPassword(token, uiState.newPassword)
		}
	}

	private fun run(onSuccess: () -> ForgotPasswordUiState, call: suspend () -> Unit) {
		uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
		viewModelScope.launch {
			try {
				call()
				uiState = onSuccess().copy(isSubmitting = false)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					// Jeden kod na token nieznany, wygasły i zużyty.
					errorMessage = if (e.error?.code == "INVALID_PASSWORD_RESET_TOKEN") {
						"Link jest nieprawidłowy albo wygasł (ważny 30 minut). Zamów nowy."
					} else {
						e.error?.message ?: "Coś poszło nie tak (HTTP ${e.status})"
					},
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSubmitting = false, errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.")
			}
		}
	}
}
