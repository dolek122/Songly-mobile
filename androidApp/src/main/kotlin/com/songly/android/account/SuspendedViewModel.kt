package com.songly.android.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.Suspension
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

data class SuspendedUiState(
	val isLoading: Boolean = true,
	val suspension: Suspension? = null,
	val errorMessage: String? = null,
	val appealText: String = "",
	val isAppealing: Boolean = false,
	val appealError: String? = null,
	val deletePassword: String = "",
	val isDeleting: Boolean = false,
	val deleteError: String? = null,
	/** Zawieszenie wygasło albo zostało zdjęte — wracamy do zwykłej aplikacji. */
	val lifted: Boolean = false,
	val sessionEnded: Boolean = false,
) {
	val canAppeal: Boolean
		get() = appealText.isNotBlank() && appealText.length <= 1000 && !isAppealing
}

/**
 * Zawieszone konto widzi wyłącznie to, na co backend mu pozwala (ADR 0022):
 * swój stan z powodem, odwołanie (ADR 0023), wylogowanie i usunięcie konta —
 * to ostatnie, bo sankcja nie może odbierać prawa z RODO (§9).
 */
class SuspendedViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(SuspendedUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			uiState = try {
				val suspension = client.mySuspension()
				uiState.copy(isLoading = false, suspension = suspension, lifted = !suspension.suspended)
			} catch (e: Exception) {
				uiState.copy(isLoading = false, errorMessage = errorText(e, "sprawdzić stanu konta"))
			}
		}
	}

	fun onAppealTextChange(value: String) {
		uiState = uiState.copy(appealText = value, appealError = null)
	}

	fun submitAppeal() {
		if (!uiState.canAppeal) return
		uiState = uiState.copy(isAppealing = true, appealError = null)
		viewModelScope.launch {
			uiState = try {
				client.appealSuspension(uiState.appealText.trim())
				uiState.copy(isAppealing = false, appealText = "", suspension = client.mySuspension())
			} catch (e: Exception) {
				uiState.copy(isAppealing = false, appealError = errorText(e, "wysłać odwołania"))
			}
		}
	}

	fun logout() {
		viewModelScope.launch {
			client.logout()
			uiState = uiState.copy(sessionEnded = true)
		}
	}

	fun onDeletePasswordChange(value: String) {
		uiState = uiState.copy(deletePassword = value, deleteError = null)
	}

	fun deleteAccount() {
		if (uiState.deletePassword.isBlank() || uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true, deleteError = null)
		viewModelScope.launch {
			uiState = try {
				client.deleteAccount(uiState.deletePassword)
				uiState.copy(isDeleting = false, sessionEnded = true)
			} catch (e: Exception) {
				uiState.copy(isDeleting = false, deleteError = errorText(e, "usunąć konta"))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
