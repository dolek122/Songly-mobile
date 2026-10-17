package com.songly.android.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.AnalyticsSummary
import com.songly.shared.model.EscalatedAppeal
import com.songly.shared.model.ModerationLogEntry
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.SuspendedAccount
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.PagedSection
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

enum class AdminSection { SUSPENSIONS, ESCALATIONS, ADMINS, ANALYTICS, LOG }

/** Okna analityki do wyboru (backend przyjmuje dowolną liczbę dni). */
val ANALYTICS_WINDOWS = listOf(7, 30, 90)

data class AdminUiState(
	val section: AdminSection = AdminSection.SUSPENSIONS,
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
	val statusMessage: String? = null,
	val suspensions: List<SuspendedAccount> = emptyList(),
	val escalations: List<EscalatedAppeal> = emptyList(),
	val adminIds: List<String> = emptyList(),
	val analyticsDays: Int = 30,
	val analytics: AnalyticsSummary? = null,
	val log: PagedSection<ModerationLogEntry> = PagedSection(),
	/** Zawężenie dziennika do jednej osoby (np. otwarte z jej profilu). */
	val logUserId: String? = null,
	/** Nazwy wyświetlane po id — wykazy admina niosą same identyfikatory. */
	val names: Map<String, String> = emptyMap(),
	val currentUserId: String? = null,
	val busyId: String? = null,
)

/**
 * Panel administratora platformy. Wszystko, co tu jest, backend gatuje rolą
 * czytaną na żywo (ADR 0006) — więc gdyby ktoś odebrał nam rolę w trakcie, każda
 * akcja po prostu dostanie 403, a nie zadziała na nieaktualnych uprawnieniach.
 */
class AdminViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(AdminUiState())
		private set

	fun select(section: AdminSection) {
		uiState = uiState.copy(section = section, statusMessage = null)
		refresh()
	}

	/** Dziennik jednej osoby — wejście z jej profilu. */
	fun showLogFor(userId: String?) {
		uiState = uiState.copy(section = AdminSection.LOG, logUserId = userId, log = PagedSection(), statusMessage = null)
		refresh()
	}

	fun selectAnalyticsWindow(days: Int) {
		uiState = uiState.copy(analyticsDays = days)
		refresh()
	}

	fun refresh() {
		val section = uiState.section
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		viewModelScope.launch {
			try {
				if (uiState.currentUserId == null) {
					uiState = uiState.copy(currentUserId = runCatching { client.currentUserId() }.getOrNull())
				}
				// Najpierw dane, potem nazwy: resolveNames zapisuje stan, więc nie może
				// siedzieć wewnątrz wyrażenia, którego wynik przypisujemy później —
				// przypisanie nadpisałoby świeżo pobrane nazwy.
				when (section) {
					AdminSection.SUSPENSIONS -> uiState = uiState.copy(suspensions = client.suspensions())
					AdminSection.ESCALATIONS -> {
						val list = client.escalatedBanAppeals()
						uiState = uiState.copy(escalations = list)
						resolveNames(list.map { it.userId })
					}
					AdminSection.ADMINS -> {
						val ids = client.admins().userIds
						uiState = uiState.copy(adminIds = ids)
						resolveNames(ids)
					}
					AdminSection.ANALYTICS -> uiState = uiState.copy(analytics = client.analyticsSummary(uiState.analyticsDays))
					AdminSection.LOG -> uiState = uiState.copy(
						log = uiState.log.replaceWith(client.platformModerationLog(uiState.logUserId, 0, PAGE_SIZE)),
					)
				}
				uiState = uiState.copy(isLoading = false)
			} catch (e: Exception) {
				uiState = uiState.copy(isLoading = false, errorMessage = errorText(e, "wczytać danych"))
			}
		}
	}

	fun loadMoreLog() {
		if (!uiState.log.canLoadMore) return
		uiState = uiState.copy(log = uiState.log.loadingMore())
		viewModelScope.launch {
			uiState = try {
				uiState.copy(log = uiState.log.append(client.platformModerationLog(uiState.logUserId, uiState.log.nextPage, PAGE_SIZE)))
			} catch (e: Exception) {
				uiState.copy(log = uiState.log.failed(errorText(e, "doczytać dziennika")))
			}
		}
	}

	// --- Zawieszenia (ADR 0022–0023) ---

	/** Zdejmuje zawieszenie. Przy oczekującym odwołaniu to jest jego uznanie. */
	fun lift(account: SuspendedAccount) = act(
		account.userId,
		success = if (account.hasPendingAppeal) "Uznano odwołanie, zawieszenie zdjęte" else "Zdjęto zawieszenie",
	) {
		client.liftSuspension(account.userId)
		uiState = uiState.copy(suspensions = uiState.suspensions.filterNot { it.userId == account.userId })
	}

	fun rejectAppeal(account: SuspendedAccount) = act(account.userId, success = "Odrzucono odwołanie") {
		client.rejectSuspensionAppeal(account.userId)
		uiState = uiState.copy(
			suspensions = uiState.suspensions.map {
				if (it.userId == account.userId) it.copy(appealStatus = "REJECTED") else it
			},
		)
	}

	// --- Eskalacje banów (ADR 0028) ---

	fun overturn(appeal: EscalatedAppeal) = act(appeal.userId + appeal.communityId, success = "Uchylono ban") {
		client.overturnBan(appeal.communityId, appeal.userId)
		removeEscalation(appeal)
	}

	fun uphold(appeal: EscalatedAppeal) = act(appeal.userId + appeal.communityId, success = "Utrzymano ban") {
		client.upholdBan(appeal.communityId, appeal.userId)
		removeEscalation(appeal)
	}

	// --- Administratorzy (ADR 0021) ---

	/** Nie sobie: to niezmiennik „zawsze zostaje co najmniej jeden admin". */
	fun revokeAdmin(userId: String) = act(userId, success = "Odebrano rolę administratora") {
		client.revokeAdmin(userId)
		uiState = uiState.copy(adminIds = uiState.adminIds - userId)
	}

	private fun removeEscalation(appeal: EscalatedAppeal) {
		uiState = uiState.copy(
			escalations = uiState.escalations.filterNot { it.userId == appeal.userId && it.communityId == appeal.communityId },
		)
	}

	/**
	 * Nazwy z profili. Zawieszonego profilu nie ma (404, ADR 0026), więc dla niego
	 * nazwy nie będzie — UI pokazuje wtedy identyfikator.
	 */
	private suspend fun resolveNames(ids: List<String>) {
		val missing = ids.filterNot { it in uiState.names }
		if (missing.isEmpty()) return
		val resolved = missing.mapNotNull { id ->
			runCatching { client.profile(id) }.getOrNull()?.let { id to it.displayName }
		}
		uiState = uiState.copy(names = uiState.names + resolved)
	}

	private fun act(id: String, success: String, block: suspend () -> Unit) {
		if (uiState.busyId != null) return
		uiState = uiState.copy(busyId = id, statusMessage = null)
		viewModelScope.launch {
			uiState = try {
				block()
				uiState.copy(busyId = null, statusMessage = success)
			} catch (e: Exception) {
				uiState.copy(busyId = null, statusMessage = errorText(e, "wykonać akcji"))
			}
		}
	}

	private fun errorText(e: Exception, action: String): String = when (e) {
		is SonglyApiException -> e.error?.message ?: "Nie udało się $action (HTTP ${e.status})"
		else -> "Brak połączenia z serwerem."
	}
}
