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

/** Stan ekranu cudzego profilu: dane ładowane po [UserProfileViewModel.userId]. */
data class UserProfileUiState(
	val isLoading: Boolean = true,
	val profile: PublicProfile? = null,
	val errorMessage: String? = null,
)

/**
 * Logika ekranu cudzego profilu (read-only). Pobiera publiczny profil innego
 * użytkownika (dane profilowe + ulubione gatunki) z modułu `profile` przez
 * `shared`. 404 rozpoznajemy osobno, bo to „użytkownik nie istnieje", nie awaria.
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
				uiState = UserProfileUiState(isLoading = false, profile = profile)
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
}
