package com.songly.android.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Genre
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Stan ekranu profilu (dane profilowe + ulubione gatunki). */
data class ProfileUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val displayName: String = "",
	val bio: String = "",
	val avatarUrl: String? = null, // tylko do wyświetlenia; zmiana awatara to upload pliku
	val isSavingProfile: Boolean = false,
	val allGenres: List<Genre> = emptyList(),
	val selectedGenreIds: Set<String> = emptySet(),
	val isSavingGenres: Boolean = false,
	val statusMessage: String? = null,
	// Usuwanie konta (RODO, hard delete — wymaga potwierdzenia hasłem).
	val showDeleteDialog: Boolean = false,
	val deletePassword: String = "",
	val isDeleting: Boolean = false,
	val deleteError: String? = null,
	val accountDeleted: Boolean = false,
) {
	val canSaveProfile: Boolean
		get() = displayName.isNotBlank() && !isSavingProfile

	// Backend wymaga zbioru niepustego (@NotEmpty).
	val canSaveGenres: Boolean
		get() = selectedGenreIds.isNotEmpty() && !isSavingGenres

	val canConfirmDelete: Boolean
		get() = deletePassword.isNotBlank() && !isDeleting
}

/**
 * Logika ekranu profilu. Ładuje profil, ulubione gatunki i listę wszystkich
 * gatunków z modułu `profile` backendu (przez `shared`). Zapis profilu i zapis
 * gatunków to dwie niezależne akcje.
 */
class ProfileViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(ProfileUiState())
		private set

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				val profile = client.myProfile()
				val favorites = client.myFavoriteGenres()
				val all = client.genres(page = 0, size = 100).content
				uiState = uiState.copy(
					isLoading = false,
					displayName = profile.displayName,
					bio = profile.bio.orEmpty(),
					avatarUrl = profile.avatarUrl,
					allGenres = all,
					selectedGenreIds = favorites.map { it.id }.toSet(),
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = e.error?.message ?: "Nie udało się wczytać profilu (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	fun onDisplayNameChange(value: String) {
		uiState = uiState.copy(displayName = value, statusMessage = null)
	}

	fun onBioChange(value: String) {
		uiState = uiState.copy(bio = value, statusMessage = null)
	}

	fun toggleGenre(id: String) {
		val selected = uiState.selectedGenreIds.toMutableSet()
		if (!selected.add(id)) selected.remove(id)
		uiState = uiState.copy(selectedGenreIds = selected, statusMessage = null)
	}

	fun saveProfile() {
		if (!uiState.canSaveProfile) return
		uiState = uiState.copy(isSavingProfile = true, statusMessage = null)
		viewModelScope.launch {
			try {
				val updated = client.updateProfile(
					displayName = uiState.displayName.trim(),
					bio = uiState.bio.trim().ifBlank { null },
				)
				uiState = uiState.copy(
					isSavingProfile = false,
					displayName = updated.displayName,
					bio = updated.bio.orEmpty(),
					statusMessage = "Zapisano profil",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSavingProfile = false, statusMessage = errorText(e, "zapisać profilu"))
			}
		}
	}

	fun saveGenres() {
		if (!uiState.canSaveGenres) return
		uiState = uiState.copy(isSavingGenres = true, statusMessage = null)
		viewModelScope.launch {
			try {
				client.setFavoriteGenres(uiState.selectedGenreIds)
				uiState = uiState.copy(isSavingGenres = false, statusMessage = "Zapisano gatunki")
			} catch (e: Exception) {
				uiState = uiState.copy(isSavingGenres = false, statusMessage = errorText(e, "zapisać gatunków"))
			}
		}
	}

	// --- Usuwanie konta (RODO, Claude.md §9) ---

	fun openDeleteDialog() {
		uiState = uiState.copy(showDeleteDialog = true, deletePassword = "", deleteError = null)
	}

	fun dismissDeleteDialog() {
		if (uiState.isDeleting) return // nie zamykaj w trakcie kasowania
		uiState = uiState.copy(showDeleteDialog = false, deletePassword = "", deleteError = null)
	}

	fun onDeletePasswordChange(value: String) {
		uiState = uiState.copy(deletePassword = value, deleteError = null)
	}

	/**
	 * Trwale usuwa konto po potwierdzeniu hasłem. Sukces czyści sesję w `shared`
	 * i ustawia [accountDeleted], co ekran przekłada na powrót do logowania. Błędne
	 * hasło zostaje w dialogu jako [deleteError].
	 */
	fun confirmDelete() {
		if (!uiState.canConfirmDelete) return
		uiState = uiState.copy(isDeleting = true, deleteError = null)
		viewModelScope.launch {
			try {
				client.deleteAccount(uiState.deletePassword)
				uiState = uiState.copy(isDeleting = false, showDeleteDialog = false, accountDeleted = true)
			} catch (e: Exception) {
				uiState = uiState.copy(isDeleting = false, deleteError = errorText(e, "usunąć konta"))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
