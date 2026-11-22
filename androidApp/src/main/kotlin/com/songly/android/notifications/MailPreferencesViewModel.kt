package com.songly.android.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.MailPreferences
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

data class MailPreferencesUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val preferences: MailPreferences? = null,
	val isSaving: Boolean = false,
	val statusMessage: String? = null,
)

class MailPreferencesViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(MailPreferencesUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			uiState = try {
				uiState.copy(isLoading = false, preferences = client.mailPreferences())
			} catch (e: Exception) {
				uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać ustawień"))
			}
		}
	}

	fun onDecisionsAboutMeChange(enabled: Boolean) = update { it.copy(decisionsAboutMe = enabled) }

	fun onModerationQueueChange(enabled: Boolean) = update { it.copy(moderationQueue = enabled) }

	private fun update(change: (MailPreferences) -> MailPreferences) {
		val previous = uiState.preferences ?: return
		if (uiState.isSaving) return
		val next = change(previous)
		uiState = uiState.copy(preferences = next, isSaving = true, statusMessage = null)
		viewModelScope.launch {
			uiState = try {
				client.updateMailPreferences(next)
				uiState.copy(isSaving = false, statusMessage = "Zapisano")
			} catch (e: Exception) {
				uiState.copy(isSaving = false, preferences = previous, statusMessage = errorText(e, "zapisać zmiany"))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
