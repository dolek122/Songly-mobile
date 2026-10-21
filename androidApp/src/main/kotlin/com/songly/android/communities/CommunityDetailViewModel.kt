package com.songly.android.communities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.MembershipStatus
import com.songly.android.post.MAX_POST_LENGTH
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.plusDistinct
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
	// Posty: mój status (ban z powodem) i pole publikacji.
	val selectedTab: CommunityTab = CommunityTab.POSTS,
	val membership: MembershipStatus? = null,
	val draft: String = "",
	val isPublishing: Boolean = false,
	val publishError: String? = null,
	// Ban członka (dialog): kogo, za co i na ile (null = bezterminowo).
	val banTarget: CommunityMember? = null,
	val banReason: String = "",
	val banDays: Int? = null,
	val isBanning: Boolean = false,
	val banError: String? = null,
	/** Członek, na którym trwa zmiana roli — blokuje jego przyciski. */
	val roleChangeUserId: String? = null,
	// Moje odwołanie od bana.
	val appealText: String = "",
	val isAppealing: Boolean = false,
	val appealError: String? = null,
) {
	val canModerate: Boolean
		get() = community?.isModerator == true

	val canBan: Boolean
		get() = banTarget != null && banReason.length <= 500 && !isBanning

	// Backend: BanAppealRequest.text max 1000 znaków.
	val canAppeal: Boolean
		get() = appealText.isNotBlank() && appealText.length <= 1000 && !isAppealing

	// Backend: nazwa @NotBlank, max 100 znaków.
	val canSaveEdit: Boolean
		get() = editName.isNotBlank() && editName.length <= 100 && !isSaving

	// Publikują wyłącznie członkowie bez aktywnego bana (backend §8); czytać może każdy.
	val canWrite: Boolean
		get() = community?.isMember == true && membership?.isBanned != true

	val canPublish: Boolean
		get() = canWrite && draft.isNotBlank() && draft.length <= MAX_POST_LENGTH && !isPublishing
}

enum class CommunityTab { POSTS, MEMBERS, MODERATION }

/** Opcje terminu bana w dialogu; `null` = bezterminowo (ADR 0014). */
val BAN_DURATIONS: List<Int?> = listOf(1, 7, 30, null)

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
				reload()
				uiState = uiState.copy(isLoading = false)
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
					members = uiState.members.plusDistinct(page.content) { it.userId },
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

	// --- Posty ---

	fun selectTab(tab: CommunityTab) {
		uiState = uiState.copy(selectedTab = tab)
	}

	fun onDraftChange(value: String) {
		uiState = uiState.copy(draft = value, publishError = null)
	}

	/**
	 * Publikuje post w społeczności. Nowy post oddajemy przez [onPublished], bo
	 * listę postów trzyma osobny [com.songly.android.post.PostListViewModel].
	 */
	fun publish(onPublished: (Post) -> Unit) {
		if (!uiState.canPublish) return
		uiState = uiState.copy(isPublishing = true, publishError = null)
		viewModelScope.launch {
			try {
				val post = client.createCommunityPost(communityId, uiState.draft.trim())
				uiState = uiState.copy(isPublishing = false, draft = "")
				onPublished(post)
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(isPublishing = false, publishError = errorText(e, "opublikować posta"))
				// Ban mógł zapaść, gdy ekran był otwarty — odśwież status, żeby schować pole.
				if (e.error?.code == "BANNED_FROM_COMMUNITY") refreshMembership()
			} catch (e: Exception) {
				uiState = uiState.copy(isPublishing = false, publishError = errorText(e, "opublikować posta"))
			}
		}
	}

	// --- Moderacja: ban członka i role ---

	fun openBanDialog(member: CommunityMember) {
		uiState = uiState.copy(banTarget = member, banReason = "", banDays = 7, banError = null)
	}

	fun dismissBanDialog() {
		if (uiState.isBanning) return
		uiState = uiState.copy(banTarget = null, banError = null)
	}

	fun onBanReasonChange(value: String) {
		uiState = uiState.copy(banReason = value, banError = null)
	}

	fun onBanDaysChange(days: Int?) {
		uiState = uiState.copy(banDays = days, banError = null)
	}

	/**
	 * Banuje i usuwa z listy członków (ban zabiera członkostwo). Powód zobaczy
	 * zbanowany w aplikacji — nie jest prywatną notatką moderacji (ADR 0015).
	 */
	fun confirmBan(onBanned: () -> Unit = {}) {
		val target = uiState.banTarget ?: return
		if (!uiState.canBan) return
		uiState = uiState.copy(isBanning = true, banError = null)
		viewModelScope.launch {
			try {
				client.banMember(communityId, target.userId, uiState.banReason.trim().ifBlank { null }, uiState.banDays)
				reload()
				uiState = uiState.copy(isBanning = false, banTarget = null, statusMessage = "Zbanowano: ${target.displayName}")
				onBanned()
			} catch (e: Exception) {
				uiState = uiState.copy(isBanning = false, banError = errorText(e, "zbanować"))
			}
		}
	}

	/** Nadaje albo odbiera rolę moderatora — wyłącznie właściciel (ADR 0011). */
	fun toggleModerator(member: CommunityMember) {
		if (uiState.roleChangeUserId != null) return
		val role = if (member.isModerator) "MEMBER" else "MODERATOR"
		uiState = uiState.copy(roleChangeUserId = member.userId, statusMessage = null)
		viewModelScope.launch {
			try {
				client.setMemberRole(communityId, member.userId, role)
				uiState = uiState.copy(
					roleChangeUserId = null,
					members = uiState.members.map { if (it.userId == member.userId) it.copy(role = role) else it },
					statusMessage = if (role == "MODERATOR") {
						"${member.displayName} jest teraz moderatorem"
					} else {
						"${member.displayName} nie jest już moderatorem"
					},
				)
			} catch (e: Exception) {
				uiState = uiState.copy(roleChangeUserId = null, statusMessage = errorText(e, "zmienić roli"))
			}
		}
	}

	// --- Moje odwołanie od bana (ADR 0017, 0028) ---

	fun onAppealTextChange(value: String) {
		uiState = uiState.copy(appealText = value, appealError = null)
	}

	/** Jedno odwołanie na ban — po wysłaniu formularz znika, zostaje jego status. */
	fun submitAppeal() {
		if (!uiState.canAppeal) return
		appealAction { client.appealBan(communityId, uiState.appealText.trim()) }
	}

	/** Odrzucone odwołanie można raz przekazać administracji platformy. */
	fun escalateAppeal() {
		if (uiState.isAppealing) return
		appealAction { client.escalateBanAppeal(communityId) }
	}

	private fun appealAction(call: suspend () -> Unit) {
		uiState = uiState.copy(isAppealing = true, appealError = null)
		viewModelScope.launch {
			try {
				call()
				refreshMembership()
				uiState = uiState.copy(isAppealing = false, appealText = "")
			} catch (e: Exception) {
				uiState = uiState.copy(isAppealing = false, appealError = errorText(e, "wysłać odwołania"))
			}
		}
	}

	private suspend fun refreshMembership() {
		// Status to dodatek do ekranu: jego awaria nie może zasłonić społeczności.
		val membership = runCatching { client.membershipStatus(communityId) }.getOrNull()
		uiState = uiState.copy(membership = membership)
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
		refreshMembership()
	}
}
