package com.songly.android.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.BackButton
import com.songly.android.moderation.logActionLabel
import com.songly.android.ui.ScreenTopBar
import com.songly.android.ui.formatDateTime
import com.songly.shared.model.EscalatedAppeal
import com.songly.shared.model.SuspendedAccount
import com.songly.shared.util.RelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
	viewModel: AdminViewModel,
	onBack: () -> Unit,
	onOpenUserProfile: (userId: String) -> Unit,
	onOpenCommunity: (communityId: String) -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) { viewModel.refresh() }

	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			ScreenTopBar(
				title = "Panel administratora",
				navigationIcon = { BackButton(onBack) },
			)
		},
	) { padding ->
		LazyColumn(
			contentPadding = PaddingValues(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
			item(key = "sections") {
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.horizontalScroll(rememberScrollState()),
				) {
					AdminSection.entries.forEach { section ->
						FilterChip(
							selected = state.section == section,
							onClick = { viewModel.select(section) },
							label = { Text(sectionLabel(section)) },
						)
					}
				}
			}
			if (state.statusMessage != null) {
				item(key = "status") {
					Text(state.statusMessage, color = MaterialTheme.colorScheme.primary)
				}
			}
			when {
				state.isLoading -> item(key = "loading") {
					Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
						CircularProgressIndicator()
					}
				}

				state.errorMessage != null -> item(key = "error") {
					Column {
						Text(state.errorMessage)
						TextButton(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
					}
				}

				else -> when (state.section) {
					AdminSection.SUSPENSIONS -> suspensionItems(state, viewModel, onOpenUserProfile)
					AdminSection.ESCALATIONS -> escalationItems(state, viewModel, onOpenUserProfile, onOpenCommunity)
					AdminSection.ADMINS -> adminItems(state, viewModel, onOpenUserProfile)
					AdminSection.ANALYTICS -> analyticsItems(state, viewModel)
					AdminSection.LOG -> logItems(state, viewModel)
				}
			}
		}
	}
}

private fun AdminUiState.nameOf(userId: String?): String =
	userId?.let { names[it] } ?: userId?.let { "konto ${it.take(8)}…" } ?: "usunięte konto"

private fun LazyListScope.empty(text: String) = item(key = "empty") {
	Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun LazyListScope.suspensionItems(state: AdminUiState, viewModel: AdminViewModel, onOpenProfile: (String) -> Unit) {
	if (state.suspensions.isEmpty()) return empty("Żadne konto nie jest zawieszone.")
	items(state.suspensions, key = { "s-${it.userId}" }) { account ->
		SuspensionCard(account, state.nameOf(account.userId), state.busyId == account.userId, viewModel, onOpenProfile)
	}
}

@Composable
private fun SuspensionCard(
	account: SuspendedAccount,
	name: String,
	busy: Boolean,
	viewModel: AdminViewModel,
	onOpenProfile: (String) -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				name,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.clickable { onOpenProfile(account.userId) },
			)
			Text(
				if (account.expiresAt == null) "Bezterminowo" else "Do ${formatDateTime(account.expiresAt!!)}",
				style = MaterialTheme.typography.labelMedium,
			)
			if (!account.reason.isNullOrBlank()) Text("Powód: ${account.reason}")
			when (account.appealStatus) {
				"PENDING" -> Text("Odwołanie czeka na decyzję", color = MaterialTheme.colorScheme.primary)
				"REJECTED" -> Text("Odwołanie odrzucone", color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			if (!account.appealText.isNullOrBlank()) Text("Odwołanie: „${account.appealText}”")
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
				if (account.hasPendingAppeal) {
					TextButton(onClick = { viewModel.rejectAppeal(account) }, enabled = !busy) { Text("Odrzuć odwołanie") }
				}
				OutlinedButton(onClick = { viewModel.lift(account) }, enabled = !busy) {
					// Zdjęcie zawieszenia przy oczekującym odwołaniu JEST jego uznaniem (ADR 0023).
					Text(if (account.hasPendingAppeal) "Uznaj odwołanie" else "Zdejmij zawieszenie")
				}
			}
		}
	}
}

private fun LazyListScope.escalationItems(
	state: AdminUiState,
	viewModel: AdminViewModel,
	onOpenProfile: (String) -> Unit,
	onOpenCommunity: (String) -> Unit,
) {
	if (state.escalations.isEmpty()) return empty("Brak odwołań przekazanych administracji.")
	items(state.escalations, key = { "e-${it.communityId}-${it.userId}" }) { appeal ->
		EscalationCard(
			appeal = appeal,
			name = state.nameOf(appeal.userId),
			busy = state.busyId == appeal.userId + appeal.communityId,
			viewModel = viewModel,
			onOpenProfile = onOpenProfile,
			onOpenCommunity = onOpenCommunity,
		)
	}
}

/** Obie strony sporu — powód bana i odwołanie — bo admin rozstrzyga między nimi (ADR 0028). */
@Composable
private fun EscalationCard(
	appeal: EscalatedAppeal,
	name: String,
	busy: Boolean,
	viewModel: AdminViewModel,
	onOpenProfile: (String) -> Unit,
	onOpenCommunity: (String) -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				name,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.clickable { onOpenProfile(appeal.userId) },
			)
			Text(
				"Społeczność: ${appeal.communityName ?: "—"}",
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.clickable { onOpenCommunity(appeal.communityId) },
			)
			Text(
				if (appeal.expiresAt == null) "Ban bezterminowy" else "Ban do ${formatDateTime(appeal.expiresAt!!)}",
				style = MaterialTheme.typography.labelMedium,
			)
			Text("Powód bana: ${appeal.reason?.takeIf { it.isNotBlank() } ?: "nie podano"}")
			Text("Odwołanie: „${appeal.appealText ?: ""}”")
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
				TextButton(onClick = { viewModel.uphold(appeal) }, enabled = !busy) { Text("Utrzymaj ban") }
				OutlinedButton(onClick = { viewModel.overturn(appeal) }, enabled = !busy) { Text("Uchyl ban") }
			}
		}
	}
}

private fun LazyListScope.adminItems(state: AdminUiState, viewModel: AdminViewModel, onOpenProfile: (String) -> Unit) {
	item(key = "admins-hint") {
		Text(
			"Rolę nadaje się z profilu użytkownika. Siebie zdegradować nie można — dzięki temu zawsze zostaje co najmniej jeden administrator.",
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
	items(state.adminIds, key = { "a-$it" }) { id ->
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
			Text(
				state.nameOf(id) + if (id == state.currentUserId) " (Ty)" else "",
				modifier = Modifier.weight(1f).clickable { onOpenProfile(id) },
			)
			if (id != state.currentUserId) {
				TextButton(
					onClick = { viewModel.revokeAdmin(id) },
					enabled = state.busyId != id,
					colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
				) { Text("Odbierz rolę") }
			}
		}
	}
}

private fun LazyListScope.analyticsItems(state: AdminUiState, viewModel: AdminViewModel) {
	item(key = "windows") {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			ANALYTICS_WINDOWS.forEach { days ->
				FilterChip(
					selected = state.analyticsDays == days,
					onClick = { viewModel.selectAnalyticsWindow(days) },
					label = { Text("$days dni") },
				)
			}
		}
	}
	val counts = state.analytics?.counts.orEmpty()
	if (counts.isEmpty()) return empty("Brak zdarzeń w tym okresie.")
	items(counts, key = { "c-${it.type}" }) { count ->
		Row(modifier = Modifier.fillMaxWidth()) {
			Text(analyticsLabel(count.type), modifier = Modifier.weight(1f))
			Text("${count.count}", fontWeight = FontWeight.SemiBold)
		}
	}
}

private fun LazyListScope.logItems(state: AdminUiState, viewModel: AdminViewModel) {
	if (state.logUserId != null) {
		item(key = "log-filter") {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Tylko: ${state.nameOf(state.logUserId)}", modifier = Modifier.weight(1f))
				TextButton(onClick = { viewModel.showLogFor(null) }) { Text("Pokaż wszystko") }
			}
		}
	}
	if (state.log.items.isEmpty()) return empty("Dziennik jest pusty.")
	items(state.log.items, key = { "l-${it.occurredAt}-${it.action}-${it.subjectId}-${it.communityId}" }) { entry ->
		Column(modifier = Modifier.fillMaxWidth()) {
			Text("${logActionLabel(entry.action)}: ${state.nameOf(entry.subjectId)}")
			val details = buildList {
				add(RelativeTime.format(entry.occurredAt))
				if (entry.actorId != null) add("przez ${state.nameOf(entry.actorId)}")
				if (entry.communityId == null) add("platforma") else add("społeczność")
				entry.expiresAt?.let { add("do ${formatDateTime(it)}") }
			}
			Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
	if (!state.log.endReached) {
		item(key = "log-more") {
			TextButton(onClick = viewModel::loadMoreLog) { Text("Pokaż więcej") }
		}
	}
}

private fun sectionLabel(section: AdminSection): String = when (section) {
	AdminSection.SUSPENSIONS -> "Zawieszenia"
	AdminSection.ESCALATIONS -> "Eskalacje banów"
	AdminSection.ADMINS -> "Administratorzy"
	AdminSection.ANALYTICS -> "Analityka"
	AdminSection.LOG -> "Dziennik"
}

private fun analyticsLabel(type: String): String = when (type) {
	"USER_REGISTERED" -> "Nowe konta"
	"POST_LIKED" -> "Polubienia"
	"POST_COMMENTED" -> "Komentarze"
	"POST_SHARED" -> "Udostępnienia"
	"FRIEND_REQUEST_SENT" -> "Wysłane zaproszenia"
	"FRIEND_REQUEST_ACCEPTED" -> "Przyjęte zaproszenia"
	"COMMUNITY_MEMBER_JOINED" -> "Dołączenia do społeczności"
	// Liczy posty, nie kliknięcia „zgłoś" — zdarzenie powstaje przy pierwszym zgłoszeniu (§13).
	"POST_FIRST_REPORTED" -> "Zgłoszone posty"
	"POST_REPORT_DISMISSED" -> "Odrzucone zgłoszenia"
	"COMMUNITY_POST_REMOVED" -> "Posty usunięte przez moderację"
	"COMMUNITY_COMMENT_REMOVED" -> "Komentarze usunięte przez moderację"
	"COMMUNITY_BAN_ISSUED_PERMANENT" -> "Bany bezterminowe"
	"COMMUNITY_BAN_ISSUED_TEMPORARY" -> "Bany czasowe"
	"BAN_APPEAL_SUBMITTED" -> "Odwołania od banów"
	"BAN_APPEAL_ACCEPTED" -> "Uznane odwołania"
	"BAN_APPEAL_REJECTED" -> "Odrzucone odwołania"
	else -> type
}
