package com.songly.android.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Genre
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

data class GenreOnboardingUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val genres: List<Genre> = emptyList(),
	val selectedIds: Set<String> = emptySet(),
	val isSaving: Boolean = false,
	val saveError: String? = null,
	val finished: Boolean = false,
	val saved: Boolean = false,
) {
	val canSave: Boolean
		get() = selectedIds.isNotEmpty() && !isSaving
}

class GenreOnboardingViewModel(
	private val client: SonglyApiClient,
	private val store: OnboardingStore,
) : ViewModel() {

	var uiState by mutableStateOf(GenreOnboardingUiState())
		private set

	fun load() {
		if (uiState.genres.isNotEmpty()) return
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			uiState = try {
				uiState.copy(isLoading = false, genres = client.genres(page = 0, size = 100).content)
			} catch (e: Exception) {
				uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać gatunków"))
			}
		}
	}

	fun toggle(genreId: String) {
		val selected = uiState.selectedIds
		uiState = uiState.copy(
			selectedIds = if (genreId in selected) selected - genreId else selected + genreId,
			saveError = null,
		)
	}

	fun save() {
		if (!uiState.canSave) return
		uiState = uiState.copy(isSaving = true, saveError = null)
		viewModelScope.launch {
			try {
				client.setFavoriteGenres(uiState.selectedIds)
				rememberDone()
				uiState = uiState.copy(isSaving = false, finished = true, saved = true)
			} catch (e: Exception) {
				uiState = uiState.copy(isSaving = false, saveError = errorText(e, "zapisać gatunków"))
			}
		}
	}

	fun skip() {
		if (uiState.isSaving) return
		viewModelScope.launch {
			rememberDone()
			uiState = uiState.copy(finished = true, saved = false)
		}
	}

	private suspend fun rememberDone() {
		runCatching { store.markDone(client.currentUserId()) }
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}

suspend fun needsGenreOnboarding(client: SonglyApiClient, store: OnboardingStore): Boolean {
	val userId = client.currentUserId()
	if (store.isDone(userId)) return false
	val hasGenres = client.myFavoriteGenres().isNotEmpty()
	if (hasGenres) store.markDone(userId)
	return !hasGenres
}
