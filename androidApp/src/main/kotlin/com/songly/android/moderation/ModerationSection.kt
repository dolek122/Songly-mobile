package com.songly.android.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songly.android.ui.formatDateTime
import com.songly.shared.model.CommunityBan
import com.songly.shared.model.ModerationLogEntry
import com.songly.shared.model.PostReport
import com.songly.shared.paging.PagedSection
import com.songly.shared.util.RelativeTime

/**
 * Zakładka „Moderacja" jako fragment `LazyColumn` ekranu społeczności.
 * [displayNameOf] tłumaczy identyfikatory z dziennika na nazwy znanych członków
 * — dziennik niesie wyłącznie identyfikatory (ADR 0024).
 */
fun LazyListScope.moderationItems(
	viewModel: CommunityModerationViewModel,
	displayNameOf: (String?) -> String,
	onOpenPost: (postId: String) -> Unit,
	onOpenUserProfile: (userId: String) -> Unit,
) {
	val state = viewModel.uiState
	item(key = "mod-sections") {
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			ModerationSection.entries.forEach { section ->
				FilterChip(
					selected = state.section == section,
					onClick = { viewModel.select(section) },
					label = { Text(sectionLabel(section)) },
				)
			}
		}
	}
	if (state.statusMessage != null) {
		item(key = "mod-status") {
			Text(state.statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
		}
	}
	when {
		state.isLoading -> item(key = "mod-loading") {
			Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
				CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
			}
		}

		state.errorMessage != null -> item(key = "mod-error") {
			Column {
				Text(state.errorMessage, style = MaterialTheme.typography.bodyMedium)
				TextButton(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
			}
		}

		state.section == ModerationSection.REPORTS -> section(
			section = state.reports,
			emptyText = "Brak zgłoszeń do rozpatrzenia.",
			key = { "report-${it.id}" },
			onLoadMore = viewModel::loadMore,
		) { report ->
			ReportCard(
				report = report,
				busy = state.busyId == report.id,
				onOpenPost = { onOpenPost(report.post.id) },
				onOpenReporter = { onOpenUserProfile(report.reporterId) },
				onRemovePost = { viewModel.removePost(report) },
				onDismiss = { viewModel.dismiss(report) },
			)
		}

		state.section == ModerationSection.BANS -> section(
			section = state.bans,
			emptyText = "Nikt nie ma bana w tej społeczności.",
			key = { "ban-${it.userId}" },
			onLoadMore = viewModel::loadMore,
		) { ban ->
			BanCard(
				ban = ban,
				busy = state.busyId == ban.userId,
				onOpenProfile = { onOpenUserProfile(ban.userId) },
				onUnban = { viewModel.unban(ban) },
				onRejectAppeal = { viewModel.rejectAppeal(ban) },
			)
		}

		else -> section(
			section = state.log,
			emptyText = "Dziennik moderacji jest pusty.",
			key = { "log-${it.occurredAt}-${it.action}-${it.subjectId}" },
			onLoadMore = viewModel::loadMore,
		) { entry -> LogRow(entry, displayNameOf) }
	}
}

private fun <T> LazyListScope.section(
	section: PagedSection<T>,
	emptyText: String,
	key: (T) -> String,
	onLoadMore: () -> Unit,
	content: @Composable (T) -> Unit,
) {
	if (section.items.isEmpty()) {
		item(key = "mod-empty") {
			Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		return
	}
	items(section.items, key = key) { content(it) }
	if (!section.endReached) {
		item(key = "mod-more") {
			Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
				TextButton(onClick = onLoadMore) { Text("Pokaż więcej") }
			}
		}
	}
}

@Composable
private fun ReportCard(
	report: PostReport,
	busy: Boolean,
	onOpenPost: () -> Unit,
	onOpenReporter: () -> Unit,
	onRemovePost: () -> Unit,
	onDismiss: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				text = reportReasonLabel(report.reason) + if (report.reportCount > 1) " · zgłoszeń: ${report.reportCount}" else "",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
			)
			if (!report.details.isNullOrBlank()) {
				Text("„${report.details}”", style = MaterialTheme.typography.bodyMedium)
			}
			// Historia zgłaszającego w tej społeczności (90 dni) — waga, nie wyrok (ADR 0025).
			Text(
				text = "Zgłosił(a): ${report.reporterDisplayName ?: "nieznany użytkownik"} · trafne: " +
					"${report.reporterUpheldCount}, odrzucone: ${report.reporterDismissedCount}",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.clickable(onClick = onOpenReporter),
			)
			Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPost)) {
				Column(modifier = Modifier.padding(12.dp)) {
					Text(report.post.authorDisplayName, style = MaterialTheme.typography.labelLarge)
					Text(report.post.content, maxLines = 4, overflow = TextOverflow.Ellipsis)
				}
			}
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
				TextButton(onClick = onDismiss, enabled = !busy) { Text("Odrzuć zgłoszenie") }
				OutlinedButton(
					onClick = onRemovePost,
					enabled = !busy,
					colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
				) { Text("Usuń post") }
			}
		}
	}
}

@Composable
private fun BanCard(
	ban: CommunityBan,
	busy: Boolean,
	onOpenProfile: () -> Unit,
	onUnban: () -> Unit,
	onRejectAppeal: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				text = ban.displayName ?: "Usunięty użytkownik",
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.clickable(onClick = onOpenProfile),
			)
			Text(
				text = if (ban.expiresAt == null) "Bezterminowo" else "Do ${formatDateTime(ban.expiresAt!!)}",
				style = MaterialTheme.typography.labelMedium,
			)
			if (!ban.reason.isNullOrBlank()) {
				Text("Powód: ${ban.reason}", style = MaterialTheme.typography.bodyMedium)
			}
			appealLabel(ban.appealStatus)?.let { label ->
				Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
			}
			if (!ban.appealText.isNullOrBlank()) {
				Text("Odwołanie: „${ban.appealText}”", style = MaterialTheme.typography.bodyMedium)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
				if (ban.hasPendingAppeal) {
					TextButton(onClick = onRejectAppeal, enabled = !busy) { Text("Odrzuć odwołanie") }
				}
				OutlinedButton(onClick = onUnban, enabled = !busy) {
					// Zdjęcie bana przy oczekującym odwołaniu JEST jego uznaniem (ADR 0017).
					Text(if (ban.hasPendingAppeal) "Uznaj odwołanie" else "Zdejmij ban")
				}
			}
		}
	}
}

@Composable
private fun LogRow(entry: ModerationLogEntry, displayNameOf: (String?) -> String) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = "${logActionLabel(entry.action)}: ${displayNameOf(entry.subjectId)}",
			style = MaterialTheme.typography.bodyMedium,
		)
		val details = buildList {
			add(RelativeTime.format(entry.occurredAt))
			if (entry.actorId != null) add("przez ${displayNameOf(entry.actorId)}")
			entry.expiresAt?.let { add("do ${formatDateTime(it)}") }
			entry.reportCount?.takeIf { it > 0 }?.let { add("zgłoszeń: $it") }
		}
		Text(
			details.joinToString(" · "),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

private fun sectionLabel(section: ModerationSection): String = when (section) {
	ModerationSection.REPORTS -> "Zgłoszenia"
	ModerationSection.BANS -> "Bany"
	ModerationSection.LOG -> "Dziennik"
}

fun appealLabel(status: String?): String? = when (status) {
	"PENDING" -> "Odwołanie czeka na rozpatrzenie"
	"REJECTED" -> "Odwołanie odrzucone przez moderację"
	"ESCALATED" -> "Odwołanie przekazane administracji"
	"ESCALATION_REJECTED" -> "Administracja utrzymała ban"
	else -> null
}

fun logActionLabel(action: String): String = when (action) {
	"COMMUNITY_BAN_ISSUED" -> "Ban"
	"COMMUNITY_BAN_LIFTED" -> "Zdjęcie bana"
	"COMMUNITY_POST_REMOVED" -> "Usunięcie posta"
	"POST_REPORT_DISMISSED" -> "Odrzucone zgłoszenie"
	"POST_REPORT_UPHELD" -> "Trafne zgłoszenie"
	"COMMUNITY_BAN_APPEAL_SUBMITTED" -> "Odwołanie od bana"
	"COMMUNITY_BAN_APPEAL_ACCEPTED" -> "Uznane odwołanie"
	"COMMUNITY_BAN_APPEAL_REJECTED" -> "Odrzucone odwołanie"
	"COMMUNITY_BAN_APPEAL_ESCALATED" -> "Odwołanie do administracji"
	"COMMUNITY_BAN_ESCALATION_ACCEPTED" -> "Administracja uchyliła ban"
	"COMMUNITY_BAN_ESCALATION_REJECTED" -> "Administracja utrzymała ban"
	"COMMUNITY_MODERATOR_GRANTED" -> "Nadanie roli moderatora"
	"COMMUNITY_MODERATOR_REVOKED" -> "Odebranie roli moderatora"
	"ACCOUNT_SUSPENDED" -> "Zawieszenie konta"
	"ACCOUNT_SUSPENSION_LIFTED" -> "Zdjęcie zawieszenia"
	"SUSPENSION_APPEAL_SUBMITTED" -> "Odwołanie od zawieszenia"
	"SUSPENSION_APPEAL_ACCEPTED" -> "Uznane odwołanie od zawieszenia"
	"SUSPENSION_APPEAL_REJECTED" -> "Odrzucone odwołanie od zawieszenia"
	"PLATFORM_ADMIN_GRANTED" -> "Nadanie roli administratora"
	"PLATFORM_ADMIN_REVOKED" -> "Odebranie roli administratora"
	else -> action
}
