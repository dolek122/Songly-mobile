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

/** Stan ekranu rejestracji — jedno źródło prawdy dla UI. */
data class RegisterUiState(
	val email: String = "",
	val password: String = "",
	val confirmPassword: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	/** Zgłoszenie przyjęte — ekran przechodzi w krok „sprawdź skrzynkę". */
	val awaitingVerification: Boolean = false,
	/** To, co użytkownik wkleił z maila: cały link albo sam token. */
	val verificationInput: String = "",
	val infoMessage: String? = null,
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

	val canVerify: Boolean
		get() = LinkToken.extract(verificationInput) != null && !isSubmitting
}

/**
 * Logika rejestracji w dwóch krokach (backend ADR 0009):
 *
 * 1. [submit] zgłasza rejestrację. Serwer odpowiada 202 bez tokenów i wysyła link
 *    potwierdzający — **konto jeszcze nie istnieje**, więc nie ma sesji.
 * 2. [verify] wysyła token z linku; dopiero wtedy konto powstaje, a serwer od razu
 *    wydaje sesję, więc [RegisterUiState.registered] wyzwala nawigację jak po
 *    logowaniu.
 *
 * Krok 1 nigdy nie mówi „adres zajęty" — backend celowo odpowiada identycznie
 * dla adresu wolnego i zajętego, a właściciel zajętego dostaje mailem ostrzeżenie.
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

	fun onVerificationInputChange(value: String) {
		uiState = uiState.copy(verificationInput = value, errorMessage = null, infoMessage = null)
	}

	fun submit() {
		if (!uiState.canSubmit) return
		sendRegistration { it.copy(awaitingVerification = true) }
	}

	/**
	 * Ponowna wysyłka linku — to samo żądanie co w kroku 1. Backend wydaje nowy
	 * link najwcześniej po minucie od poprzedniego i odpowiada tak samo, gdy
	 * wysyłkę wyciszy, więc komunikat nie może obiecywać, że mail na pewno wyszedł.
	 */
	fun resend() {
		if (uiState.isSubmitting) return
		sendRegistration {
			it.copy(infoMessage = "Jeśli od poprzedniego minęła co najmniej minuta, wysłaliśmy nowy link.")
		}
	}

	/** Wraca do formularza, np. po literówce w adresie. */
	fun editEmail() {
		uiState = uiState.copy(
			awaitingVerification = false,
			verificationInput = "",
			errorMessage = null,
			infoMessage = null,
		)
	}

	/**
	 * Wejście z linku w mailu (`songly://verify-email`, ADR 0037): od razu potwierdza
	 * adres. Kliknięcie linku *jest* decyzją użytkownika, więc nie każemy mu jeszcze
	 * raz naciskać „Potwierdź". Adresu nie znamy — link mógł przyjść z rejestracji
	 * zaczętej na innym urządzeniu — i ekran musi to uwzględnić.
	 */
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
					// Jeden kod na token nieznany, wygasły i zużyty — rozróżnić się nie da.
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
