package com.songly.android.communities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Stan ekranu szczegółów społeczności: nagłówek + lista członków (paginacja). */
data class CommunityDetailUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val community: Community? = null,
	val members: List<CommunityMember> = emptyList(),
	val isLoadingMoreMembers: Boolean = false,
	val membersEndReached: Boolean = false,
	val isBusy: Boolean = false, // dołączanie/opuszczanie w toku
	val statusMessage: String? = null,
	// Zarządzanie właścicielskie: edycja i usuwanie.
	val showEditDialog: Boolean = false,
	val editName: String = "",
	val editDescription: String = "",
	val isSaving: Boolean = false,
	val editError: String? = null,
	val showDeleteDialog: Boolean = false,
	val isDeleting: Boolean = false,
	val deleteError: String? = null,
	val deleted: Boolean = false, // po usunięciu — ekran wraca do listy
) {
	// Backend: nazwa @NotBlank, max 100 znaków.
	val canSaveEdit: Boolean
		get() = editName.isNotBlank() && editName.length <= 100 && !isSaving
}

/**
 * Logika ekranu szczegółów jednej społeczności (moduł community przez `shared`).
 * Ładuje dane społeczności i pierwszą stronę członków; dołączanie/opuszczanie
 * odświeża oba (zmienia się i `myRole`, i lista/licznik członków).
 */
class CommunityDetailViewModel(
	private val client: SonglyApiClient,
	private val communityId: String,
) : ViewModel() {

	var uiState by mutableStateOf(CommunityDetailUiState())
		private set

	private var nextMembersPage = 0
	private var loadingMembers = false

	fun load() {
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		nextMembersPage = 0
		viewModelScope.launch {
			try {
				val community = client.community(communityId)
				val membersPage = client.communityMembers(communityId, page = 0, size = PAGE_SIZE)
				nextMembersPage = membersPage.number + 1
				uiState = uiState.copy(
					isLoading = false,
					community = community,
					members = membersPage.content,
					membersEndReached = membersPage.last,
				)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isLoading = false,
					errorMessage = e.error?.message ?: "Nie udało się wczytać społeczności (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	fun loadMoreMembers() {
		if (loadingMembers || uiState.membersEndReached || uiState.isLoading) return
		loadingMembers = true
		uiState = uiState.copy(isLoadingMoreMembers = true)
		viewModelScope.launch {
			try {
				val page = client.communityMembers(communityId, page = nextMembersPage, size = PAGE_SIZE)
				nextMembersPage = page.number + 1
				uiState = uiState.copy(
					isLoadingMoreMembers = false,
					members = uiState.members + page.content,
					membersEndReached = page.last,
				)
			} catch (e: Exception) {
				// Doczytywanie członków best-effort — po cichu przerywamy, lista zostaje.
				uiState = uiState.copy(isLoadingMoreMembers = false)
			} finally {
				loadingMembers = false
			}
		}
	}

	/** Dołącza lub opuszcza; po sukcesie przeładowuje szczegóły i członków. */
	fun toggleMembership() {
		val community = uiState.community ?: return
		if (uiState.isBusy) return
		val join = !community.isMember
		uiState = uiState.copy(isBusy = true, statusMessage = null)
		viewModelScope.launch {
			try {
				if (join) client.joinCommunity(communityId) else client.leaveCommunity(communityId)
				reload()
				uiState = uiState.copy(isBusy = false, statusMessage = if (join) "Dołączono" else "Opuszczono")
			} catch (e: SonglyApiException) {
				val label = if (join) "dołączyć" else "opuścić"
				uiState = uiState.copy(
					isBusy = false,
					statusMessage = e.error?.message ?: "Nie udało się $label (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isBusy = false, statusMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	// --- Zarządzanie właścicielskie (edycja / usuwanie) ---

	fun openEditDialog() {
		val community = uiState.community ?: return
		uiState = uiState.copy(
			showEditDialog = true,
			editName = community.name,
			editDescription = community.description.orEmpty(),
			editError = null,
		)
	}

	fun dismissEditDialog() {
		if (uiState.isSaving) return
		uiState = uiState.copy(showEditDialog = false, editError = null)
	}

	fun onEditNameChange(value: String) {
		uiState = uiState.copy(editName = value, editError = null)
	}

	fun onEditDescriptionChange(value: String) {
		uiState = uiState.copy(editDescription = value, editError = null)
	}

	fun confirmEdit() {
		if (!uiState.canSaveEdit) return
		uiState = uiState.copy(isSaving = true, editError = null)
		viewModelScope.launch {
			try {
				val updated = client.updateCommunity(
					communityId = communityId,
					name = uiState.editName.trim(),
					description = uiState.editDescription.trim().ifBlank { null },
				)
				uiState = uiState.copy(
					isSaving = false,
					showEditDialog = false,
					community = updated,
					statusMessage = "Zapisano zmiany",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(isSaving = false, editError = errorText(e, "zapisać zmian"))
			}
		}
	}

	fun openDeleteDialog() {
		uiState = uiState.copy(showDeleteDialog = true, deleteError = null)
	}

	fun dismissDeleteDialog() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(showDeleteDialog = false, deleteError = null)
	}

	fun confirmDelete() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true, deleteError = null)
		viewModelScope.launch {
			try {
				client.deleteCommunity(communityId)
				uiState = uiState.copy(isDeleting = false, showDeleteDialog = false, deleted = true)
			} catch (e: Exception) {
				uiState = uiState.copy(isDeleting = false, deleteError = errorText(e, "usunąć społeczności"))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}

	private suspend fun reload() {
		nextMembersPage = 0
		val community = client.community(communityId)
		val membersPage = client.communityMembers(communityId, page = 0, size = PAGE_SIZE)
		nextMembersPage = membersPage.number + 1
		uiState = uiState.copy(
			community = community,
			members = membersPage.content,
			membersEndReached = membersPage.last,
		)
	}
}
