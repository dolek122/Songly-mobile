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
)

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
