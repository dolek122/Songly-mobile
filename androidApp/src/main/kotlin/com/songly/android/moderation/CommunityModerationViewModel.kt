package com.songly.android.moderation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.CommunityBan
import com.songly.shared.model.ModerationLogEntry
import com.songly.shared.model.Page
import com.songly.shared.model.PostReport
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.PagedSection
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

enum class ModerationSection { REPORTS, BANS, LOG }

data class CommunityModerationUiState(
	val section: ModerationSection = ModerationSection.REPORTS,
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
	val reports: PagedSection<PostReport> = PagedSection(),
	val bans: PagedSection<CommunityBan> = PagedSection(),
	val log: PagedSection<ModerationLogEntry> = PagedSection(),
	/** Pozycja (zgłoszenie albo ban), na której trwa akcja — blokuje jej przyciski. */
	val busyId: String? = null,
	val statusMessage: String? = null,
)

/**
 * Narzędzia moderacji jednej społeczności (właściciel i moderatorzy, ADR 0011):
 * kolejka zgłoszeń, bany z odwołaniami i dziennik decyzji (ADR 0024).
 *
 * Każda sekcja ładuje się przy pierwszym wejściu, nie wszystkie naraz — moderator
 * otwiera zwykle kolejkę, a dziennik bywa długi.
 */
class CommunityModerationViewModel(
	private val client: SonglyApiClient,
	private val communityId: String,
) : ViewModel() {

	var uiState by mutableStateOf(CommunityModerationUiState())
		private set

	private val loaded = mutableSetOf<ModerationSection>()

	fun select(section: ModerationSection) {
		uiState = uiState.copy(section = section, statusMessage = null)
		if (section !in loaded) refresh()
	}

	fun refresh() {
		val section = uiState.section
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				uiState = when (section) {
					ModerationSection.REPORTS -> uiState.copy(reports = uiState.reports.replaceWith(loadReports(0)))
					ModerationSection.BANS -> uiState.copy(bans = uiState.bans.replaceWith(loadBans(0)))
					ModerationSection.LOG -> uiState.copy(log = uiState.log.replaceWith(loadLog(0)))
				}.copy(isLoading = false)
				loaded += section
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać danych moderacji"))
			}
		}
	}

	fun loadMore() {
		val section = uiState.section
		viewModelScope.launch {
			uiState = try {
				when (section) {
					ModerationSection.REPORTS -> uiState.copy(reports = uiState.reports.append(loadReports(uiState.reports.nextPage)) { it.id })
					ModerationSection.BANS -> uiState.copy(bans = uiState.bans.append(loadBans(uiState.bans.nextPage)) { it.userId })
					ModerationSection.LOG -> uiState.copy(log = uiState.log.append(loadLog(uiState.log.nextPage)))
				}
			} catch (e: Exception) {
				uiState.copy(statusMessage = errorText(e, "doczytać listy"))
			}
		}
	}

	/**
	 * Uwzględnia zgłoszenie, czyli usuwa post. Znikają wtedy **wszystkie**
	 * zgłoszenia tego posta (kaskada na backendzie), a każdy zgłaszający dostaje
	 * zapis trafienia (ADR 0027).
	 */
	fun removePost(report: PostReport) = act(report.id, success = "Usunięto post") {
		client.deletePost(report.post.id)
		uiState = uiState.copy(reports = uiState.reports.removeIf { it.post.id == report.post.id })
	}

	/** Odrzuca zgłoszenie — post zostaje, zgłaszający dostaje powiadomienie. */
	fun dismiss(report: PostReport) = act(report.id, success = "Odrzucono zgłoszenie") {
		client.dismissReport(communityId, report.id)
		uiState = uiState.copy(reports = uiState.reports.removeIf { it.id == report.id })
	}

	/** Zdejmuje ban. Przy oczekującym odwołaniu to jest jego uznanie (ADR 0017). */
	fun unban(ban: CommunityBan) = act(ban.userId, success = if (ban.hasPendingAppeal) "Uznano odwołanie" else "Zdjęto ban") {
		client.unbanMember(communityId, ban.userId)
		uiState = uiState.copy(bans = uiState.bans.removeIf { it.userId == ban.userId })
	}

	fun rejectAppeal(ban: CommunityBan) = act(ban.userId, success = "Odrzucono odwołanie") {
		client.rejectBanAppeal(communityId, ban.userId)
		uiState = uiState.copy(
			bans = uiState.bans.copy(
				items = uiState.bans.items.map { if (it.userId == ban.userId) it.copy(appealStatus = "REJECTED") else it },
			),
		)
	}

	/** Po banie z ekranu członków — lista banów przeładuje się przy następnym wejściu. */
	fun invalidateBans() {
		loaded -= ModerationSection.BANS
		loaded -= ModerationSection.LOG
	}

	private fun act(id: String, success: String, block: suspend () -> Unit) {
		if (uiState.busyId != null) return
		uiState = uiState.copy(busyId = id, statusMessage = null)
		viewModelScope.launch {
			try {
				block()
				uiState = uiState.copy(busyId = null, statusMessage = success)
				loaded -= ModerationSection.LOG
			} catch (e: Exception) {
				uiState = uiState.copy(busyId = null, statusMessage = errorText(e, "wykonać akcji"))
			}
		}
	}

	private suspend fun loadReports(page: Int): Page<PostReport> = client.postReports(communityId, page, PAGE_SIZE)

	private suspend fun loadBans(page: Int): Page<CommunityBan> = client.communityBans(communityId, page, PAGE_SIZE)

	private suspend fun loadLog(page: Int): Page<ModerationLogEntry> =
		client.communityModerationLog(communityId, page, PAGE_SIZE)

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
