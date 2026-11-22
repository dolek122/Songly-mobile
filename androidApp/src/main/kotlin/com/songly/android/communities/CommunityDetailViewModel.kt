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

data class CommunityDetailUiState(
	val isLoading: Boolean = true,
	val errorMessage: String? = null,
	val community: Community? = null,
	val members: List<CommunityMember> = emptyList(),
	val isLoadingMoreMembers: Boolean = false,
	val membersEndReached: Boolean = false,
	val isBusy: Boolean = false,
	val statusMessage: String? = null,
	val showEditDialog: Boolean = false,
	val editName: String = "",
	val editDescription: String = "",
	val isSaving: Boolean = false,
	val editError: String? = null,
	val showDeleteDialog: Boolean = false,
	val isDeleting: Boolean = false,
	val deleteError: String? = null,
	val deleted: Boolean = false,
	val selectedTab: CommunityTab = CommunityTab.POSTS,
	val membership: MembershipStatus? = null,
	val draft: String = "",
	val isPublishing: Boolean = false,
	val publishError: String? = null,
	val banTarget: CommunityMember? = null,
	val banReason: String = "",
	val banDays: Int? = null,
	val isBanning: Boolean = false,
	val banError: String? = null,
	val roleChangeUserId: String? = null,
	val appealText: String = "",
	val isAppealing: Boolean = false,
	val appealError: String? = null,
) {
	val canModerate: Boolean
		get() = community?.isModerator == true

	val canBan: Boolean
		get() = banTarget != null && banReason.length <= 500 && !isBanning

	val canAppeal: Boolean
		get() = appealText.isNotBlank() && appealText.length <= 1000 && !isAppealing

	val canSaveEdit: Boolean
		get() = editName.isNotBlank() && editName.length <= 100 && !isSaving

	val canWrite: Boolean
		get() = community?.isMember == true && membership?.isBanned != true

	val canPublish: Boolean
		get() = canWrite && draft.isNotBlank() && draft.length <= MAX_POST_LENGTH && !isPublishing
}

enum class CommunityTab { POSTS, MEMBERS, MODERATION }

val BAN_DURATIONS: List<Int?> = listOf(1, 7, 30, null)

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
				uiState = uiState.copy(isLoadingMoreMembers = false)
			} finally {
				loadingMembers = false
			}
		}
	}

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

	fun selectTab(tab: CommunityTab) {
		uiState = uiState.copy(selectedTab = tab)
	}

	fun onDraftChange(value: String) {
		uiState = uiState.copy(draft = value, publishError = null)
	}

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
				if (e.error?.code == "BANNED_FROM_COMMUNITY") refreshMembership()
			} catch (e: Exception) {
				uiState = uiState.copy(isPublishing = false, publishError = errorText(e, "opublikować posta"))
			}
		}
	}

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

	fun onAppealTextChange(value: String) {
		uiState = uiState.copy(appealText = value, appealError = null)
	}

	fun submitAppeal() {
		if (!uiState.canAppeal) return
		appealAction { client.appealBan(communityId, uiState.appealText.trim()) }
	}

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
