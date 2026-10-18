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

/** Stan ekranu preferencji poczty. [preferences] jest `null`, dopóki nie wczytamy ich z serwera. */
data class MailPreferencesUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null, // błąd wczytania (nie ma czego pokazać)
	val preferences: MailPreferences? = null,
	val isSaving: Boolean = false,
	val statusMessage: String? = null, // wynik ostatniego zapisu
)

/**
 * Preferencje poczty (ADR 0020 backendu): dwa przełączniki, decyzje o mnie i
 * wezwania do kolejki moderacji. Dotyczą wyłącznie e-maila — skrzynka w aplikacji
 * jest kompletna zawsze, i ekran ma to mówić, żeby nikt nie szukał tu sposobu na
 * wyciszenie powiadomień.
 *
 * `PUT` zastępuje oba pola naraz, więc zmiany idą po jednej: dwa przełączenia w
 * locie mogłyby dojść w odwrotnej kolejności i drugie nadpisałoby pierwsze starą
 * wartością. Przełącznik przestawiamy od razu i cofamy przy odmowie serwera, jak
 * widoczność statusu w now-playing — inaczej UI kłamałby o tym, co wyjdzie mailem.
 */
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
