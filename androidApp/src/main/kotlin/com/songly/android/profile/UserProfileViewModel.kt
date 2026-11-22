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

data class UserProfileUiState(
	val isLoading: Boolean = true,
	val profile: PublicProfile? = null,
	val errorMessage: String? = null,
	val isSelf: Boolean = false,
	val isInviting: Boolean = false,
	val inviteSent: Boolean = false,
	val inviteMessage: String? = null,
	val showBlockDialog: Boolean = false,
	val isBlocking: Boolean = false,
	val blocked: Boolean = false,
	val blockMessage: String? = null,
	val showSuspendDialog: Boolean = false,
	val suspendReason: String = "",
	val suspendDays: Int? = 7,
	val isAdminActionRunning: Boolean = false,
	val adminMessage: String? = null,
	val suspendError: String? = null,
) {
	val canSuspend: Boolean
		get() = suspendReason.isNotBlank() && suspendReason.length <= 500 && !isAdminActionRunning
}

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
				val blocked = !isSelf && runCatching { client.isBlocking(userId) }.getOrDefault(false)
				uiState = UserProfileUiState(isLoading = false, profile = profile, isSelf = isSelf, blocked = blocked)
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

	fun openBlockDialog() {
		uiState = uiState.copy(showBlockDialog = true, blockMessage = null)
	}

	fun dismissBlockDialog() {
		if (uiState.isBlocking) return
		uiState = uiState.copy(showBlockDialog = false)
	}

	fun block(onChanged: () -> Unit = {}) {
		if (uiState.isBlocking) return
		uiState = uiState.copy(isBlocking = true, blockMessage = null)
		viewModelScope.launch {
			uiState = try {
				client.blockUser(userId)
				onChanged()
				uiState.copy(
					isBlocking = false,
					showBlockDialog = false,
					blocked = true,
					inviteSent = false,
					inviteMessage = null,
					blockMessage = "Zablokowano. Nie zobaczycie nawzajem swoich postów i nie możecie się zaczepiać.",
				)
			} catch (e: Exception) {
				uiState.copy(isBlocking = false, showBlockDialog = false, blockMessage = actionErrorText(e, "zablokować"))
			}
		}
	}

	fun unblock(onChanged: () -> Unit = {}) {
		if (uiState.isBlocking) return
		uiState = uiState.copy(isBlocking = true, blockMessage = null)
		viewModelScope.launch {
			uiState = try {
				client.unblockUser(userId)
				onChanged()
				uiState.copy(
					isBlocking = false,
					blocked = false,
					blockMessage = "Odblokowano. Jeśli byliście znajomymi, zaproszenie trzeba wysłać od nowa.",
				)
			} catch (e: Exception) {
				uiState.copy(isBlocking = false, blockMessage = actionErrorText(e, "odblokować"))
			}
		}
	}

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

	fun suspend() {
		if (!uiState.canSuspend) return
		uiState = uiState.copy(isAdminActionRunning = true, suspendError = null)
		viewModelScope.launch {
			uiState = try {
				client.suspendUser(userId, uiState.suspendReason.trim(), uiState.suspendDays)
				uiState.copy(isAdminActionRunning = false, showSuspendDialog = false, adminMessage = "Konto zawieszone")
			} catch (e: Exception) {
				uiState.copy(isAdminActionRunning = false, suspendError = actionErrorText(e, "zawiesić konta"))
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
				uiState.copy(isAdminActionRunning = false, adminMessage = actionErrorText(e, "nadać roli"))
			}
		}
	}

	private fun actionErrorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}

	fun invite() {
		if (uiState.isInviting || uiState.inviteSent) return
		uiState = uiState.copy(isInviting = true, inviteMessage = null)
		viewModelScope.launch {
			try {
				val request = client.sendFriendRequest(userId)
				val message = if (request.status == "ACCEPTED") "Jesteście znajomymi" else "Zaproszenie wysłane"
				uiState = uiState.copy(isInviting = false, inviteSent = true, inviteMessage = message)
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
