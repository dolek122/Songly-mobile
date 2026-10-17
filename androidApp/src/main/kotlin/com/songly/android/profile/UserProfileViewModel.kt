package com.songly.android.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.PublicProfile
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/**
 * Stan ekranu cudzego profilu: dane ładowane po [UserProfileViewModel.userId].
 * [isSelf] chowa akcję zaproszenia na własnym profilu (można tu trafić np. z
 * autora własnego posta). Status znajomości nie jest znany z góry, więc przycisk
 * pokazujemy zawsze, a odrzucenie (już znajomi / oczekujące / blokada) pokazujemy
 * po fakcie w [inviteMessage].
 */
data class UserProfileUiState(
	val isLoading: Boolean = true,
	val profile: PublicProfile? = null,
	val errorMessage: String? = null,
	val isSelf: Boolean = false,
	val isInviting: Boolean = false,
	val inviteSent: Boolean = false,
	val inviteMessage: String? = null,
	// Akcje administratora platformy (widoczne tylko dla admina, nie na własnym profilu).
	val showSuspendDialog: Boolean = false,
	val suspendReason: String = "",
	val suspendDays: Int? = 7,
	val isAdminActionRunning: Boolean = false,
	val adminMessage: String? = null,
	val suspendError: String? = null,
) {
	// Backend: SuspendAccountRequest.reason wymagany, max 500 — zawieszony go zobaczy.
	val canSuspend: Boolean
		get() = suspendReason.isNotBlank() && suspendReason.length <= 500 && !isAdminActionRunning
}

/**
 * Logika ekranu cudzego profilu (read-only poza akcją zaproszenia). Pobiera
 * publiczny profil innego użytkownika (dane profilowe + ulubione gatunki) z modułu
 * `profile` przez `shared`. 404 rozpoznajemy osobno, bo to „użytkownik nie
 * istnieje", nie awaria.
 */
class UserProfileViewModel(
	private val client: SonglyApiClient,
	private val userId: String,
) : ViewModel() {

	var uiState by mutableStateOf(UserProfileUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val profile = client.profile(userId)
				val isSelf = runCatching { client.currentUserId() }.getOrNull() == userId
				uiState = UserProfileUiState(isLoading = false, profile = profile, isSelf = isSelf)
			} catch (e: SonglyApiException) {
				val message = if (e.status == 404) {
					"Nie znaleziono tego użytkownika."
				} else {
					e.error?.message ?: "Nie udało się wczytać profilu (HTTP ${e.status})"
				}
				uiState = UserProfileUiState(isLoading = false, errorMessage = message)
			} catch (e: Exception) {
				uiState = UserProfileUiState(isLoading = false, errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.")
			}
		}
	}

	// --- Akcje administratora (ADR 0021, 0022) ---

	fun openSuspendDialog() {
		uiState = uiState.copy(showSuspendDialog = true, suspendReason = "", suspendDays = 7, suspendError = null)
	}

	fun dismissSuspendDialog() {
		if (uiState.isAdminActionRunning) return
		uiState = uiState.copy(showSuspendDialog = false)
	}

	fun onSuspendReasonChange(value: String) {
		uiState = uiState.copy(suspendReason = value, suspendError = null)
	}

	fun onSuspendDaysChange(days: Int?) {
		uiState = uiState.copy(suspendDays = days)
	}

	/**
	 * Zawieszenie działa od razu (rola i stan czytane na żywo, ADR 0006), a profil
	 * zawieszonego znika dla wszystkich (ADR 0026) — także z tego ekranu po
	 * odświeżeniu. Odmowy: siebie i innego admina zawiesić nie można.
	 */
	fun suspend() {
		if (!uiState.canSuspend) return
		uiState = uiState.copy(isAdminActionRunning = true, suspendError = null)
		viewModelScope.launch {
			uiState = try {
				client.suspendUser(userId, uiState.suspendReason.trim(), uiState.suspendDays)
				uiState.copy(isAdminActionRunning = false, showSuspendDialog = false, adminMessage = "Konto zawieszone")
			} catch (e: Exception) {
				uiState.copy(isAdminActionRunning = false, suspendError = adminErrorText(e, "zawiesić konta"))
			}
		}
	}

	fun grantAdmin() {
		if (uiState.isAdminActionRunning) return
		uiState = uiState.copy(isAdminActionRunning = true, adminMessage = null)
		viewModelScope.launch {
			uiState = try {
				client.grantAdmin(userId)
				uiState.copy(isAdminActionRunning = false, adminMessage = "Nadano rolę administratora")
			} catch (e: Exception) {
				uiState.copy(isAdminActionRunning = false, adminMessage = adminErrorText(e, "nadać roli"))
			}
		}
	}

	private fun adminErrorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}

	/** Wysyła zaproszenie do znajomych; po sukcesie blokuje przycisk, błąd pokazuje po fakcie. */
	fun invite() {
		if (uiState.isInviting || uiState.inviteSent) return
		uiState = uiState.copy(isInviting = true, inviteMessage = null)
		viewModelScope.launch {
			try {
				client.sendFriendRequest(userId)
				uiState = uiState.copy(isInviting = false, inviteSent = true, inviteMessage = "Wysłano zaproszenie")
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isInviting = false,
					inviteMessage = e.error?.message ?: "Nie udało się wysłać zaproszenia (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isInviting = false, inviteMessage = "Brak połączenia z serwerem.")
			}
		}
	}
}
