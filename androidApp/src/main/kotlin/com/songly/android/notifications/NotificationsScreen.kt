package com.songly.android.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.accessibleAction
import com.songly.shared.model.Notification
import com.songly.shared.util.RelativeTime

/**
 * Ekran powiadomień (Compose, natywny UI — §14): lista najnowszych zdarzeń
 * (lajki, komentarze, udostępnienia, zaproszenia, dołączenia do społeczności) z
 * paginacją. Nieprzeczytane wyróżnione i klikalne (oznacz jako przeczytane);
 * w pasku akcja „oznacz wszystkie". Stan z [NotificationsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
	viewModel: NotificationsViewModel,
	onBack: () -> Unit,
	onOpenUserProfile: (String) -> Unit,
	onOpenPost: (postId: String) -> Unit,
	onOpenCommunity: (communityId: String) -> Unit = {},
	onOpenFriends: () -> Unit = {},
	onOpenMailPreferences: () -> Unit = {},
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()

	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

	// Infinite scroll — jak w feedzie: doczytaj przy zbliżeniu do końca listy.
	val shouldLoadMore by remember {
		derivedStateOf {
			val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			val total = listState.layoutInfo.totalItemsCount
			total > 0 && lastVisible >= total - 3
		}
	}
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) viewModel.loadNextPage()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(if (state.hasUnread) "Powiadomienia (${state.unreadCount})" else "Powiadomienia") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
				actions = {
					if (state.hasUnread) {
						TextButton(onClick = viewModel::markAllRead) { Text("Oznacz wszystkie") }
					}
					TextButton(onClick = onOpenMailPreferences) { Text("E-mail") }
				},
			)
		},
	) { padding ->
		PullToRefreshBox(
			isRefreshing = state.isRefreshing,
			onRefresh = viewModel::pullToRefresh,
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
			when {
				state.isLoading && state.notifications.isEmpty() ->
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> CenteredMessage {
					Text(state.errorMessage)
					Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				state.isEmpty -> CenteredMessage {
					Text("Brak powiadomień.", style = MaterialTheme.typography.bodyMedium)
				}

				else -> LazyColumn(
					state = listState,
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					items(state.notifications, key = { it.id }) { notification ->
						NotificationCard(
							notification = notification,
							onClick = {
								viewModel.markRead(notification)
								when (val target = targetOf(notification)) {
									is NotificationTarget.Post -> onOpenPost(target.postId)
									is NotificationTarget.Community -> onOpenCommunity(target.communityId)
									NotificationTarget.Friends -> onOpenFriends()
									null -> Unit
								}
							},
							onOpenProfile = { onOpenUserProfile(notification.actorId) },
						)
					}
					if (state.isLoadingMore || state.appendErrorMessage != null) {
						item(key = "footer") {
							Footer(
								isLoading = state.isLoadingMore,
								errorMessage = state.appendErrorMessage,
								onRetry = viewModel::retryNextPage,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun NotificationCard(notification: Notification, onClick: () -> Unit, onOpenProfile: () -> Unit) {
	val unread = notification.isUnread
	Card(
		// Klikalna, gdy jest co zrobić: oznaczyć jako przeczytane albo otworzyć cel.
		// Nieprzeczytane wyróżnia tylko kolor tła i kropka — czytnik ekranu dostaje to
		// samo słowem.
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = unread || targetOf(notification) != null, onClick = onClick)
			.semantics { if (unread) stateDescription = "nieprzeczytane" },
		colors = if (unread) {
			CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
		} else {
			CardDefaults.cardColors()
		},
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			// Avatar aktora prowadzi do jego profilu (osobny tap-target nad kliknięciem karty).
			Avatar(
				displayName = notification.actorDisplayName ?: "?",
				avatarUrl = notification.actorAvatarUrl,
				size = 40.dp,
				modifier = Modifier.accessibleAction(
					description = "Profil: ${notification.actorDisplayName ?: "nieznany użytkownik"}",
					onClick = onOpenProfile,
				),
			)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = notificationText(notification),
					style = MaterialTheme.typography.bodyMedium,
					fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
				)
				Text(
					text = RelativeTime.format(notification.createdAt),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 2.dp),
				)
			}
			// Kropka „nieprzeczytane".
			if (unread) {
				Box(
					modifier = Modifier
						.size(10.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.primary),
				)
			}
		}
	}
}

/** Dokąd prowadzi powiadomienie — `resourceId` znaczy co innego zależnie od typu. */
internal sealed interface NotificationTarget {
	data class Post(val postId: String) : NotificationTarget
	data class Community(val communityId: String) : NotificationTarget
	data object Friends : NotificationTarget
}

/**
 * Cel powiadomienia według tego, co backend wkłada do `resourceId`: post przy
 * lajku, komentarzu, udostępnieniu i zgłoszeniach; społeczność przy członkostwie,
 * banach, odwołaniach i rolach. Otrzymane zaproszenie prowadzi do ekranu
 * znajomych, bo tylko tam da się je przyjąć albo odrzucić; przyjęte — do profilu
 * nadawcy (awatar), bo nie ma tu już nic do zrobienia.
 *
 * `POST_REPORT_DISMISSED` i `COMMUNITY_POST_REMOVED` celowo nie prowadzą do
 * posta: pierwszy dotyczy cudzego posta, który zostaje, ale zgłaszający niczego
 * tam już nie zrobi, a drugi — posta, którego już nie ma.
 */
internal fun targetOf(notification: Notification): NotificationTarget? = when (notification.type) {
	"POST_LIKED", "POST_COMMENTED", "POST_SHARED", "COMMUNITY_POST_REPORTED" ->
		NotificationTarget.Post(notification.resourceId)

	"COMMUNITY_MEMBER_JOINED", "COMMUNITY_BANNED", "COMMUNITY_POST_REMOVED", "BAN_APPEAL_SUBMITTED",
	"BAN_APPEAL_ACCEPTED", "BAN_APPEAL_REJECTED", "BAN_ESCALATION_ACCEPTED", "BAN_ESCALATION_REJECTED",
	"COMMUNITY_MODERATOR_GRANTED", "COMMUNITY_MODERATOR_REVOKED",
	-> NotificationTarget.Community(notification.resourceId)

	"FRIEND_REQUEST_RECEIVED" -> NotificationTarget.Friends

	else -> null
}

/** Mapuje typ powiadomienia + nazwę autora na zdanie po polsku. */
internal fun notificationText(notification: Notification): String {
	val actor = notification.actorDisplayName ?: "Ktoś"
	// Decyzje moderacyjne podpisujemy funkcją, nie osobą: ban należy do
	// społeczności, a nie do moderatora (ADR 0015).
	return when (notification.type) {
		"POST_LIKED" -> "$actor polubił(a) Twój post"
		"POST_COMMENTED" -> "$actor skomentował(a) Twój post"
		"POST_SHARED" -> "$actor udostępnił(a) Twój post"
		"FRIEND_REQUEST_RECEIVED" -> "$actor wysłał(a) Ci zaproszenie do znajomych"
		"FRIEND_REQUEST_ACCEPTED" -> "$actor zaakceptował(a) Twoje zaproszenie do znajomych"
		"COMMUNITY_MEMBER_JOINED" -> "$actor dołączył(a) do Twojej społeczności"
		"COMMUNITY_BANNED" -> "Moderacja zbanowała Cię w społeczności"
		"COMMUNITY_POST_REMOVED" -> "Moderacja usunęła Twój post w społeczności"
		"COMMUNITY_POST_REPORTED" -> "Nowe zgłoszenie posta do rozpatrzenia"
		"POST_REPORT_DISMISSED" -> "Moderacja odrzuciła Twoje zgłoszenie"
		"BAN_APPEAL_SUBMITTED" -> "Nowe odwołanie od bana do rozpatrzenia"
		"BAN_APPEAL_ACCEPTED" -> "Moderacja uznała Twoje odwołanie — ban zdjęty"
		"BAN_APPEAL_REJECTED" -> "Moderacja odrzuciła Twoje odwołanie od bana"
		"BAN_ESCALATION_ACCEPTED" -> "Administracja uchyliła Twój ban"
		"BAN_ESCALATION_REJECTED" -> "Administracja utrzymała Twój ban"
		"COMMUNITY_MODERATOR_GRANTED" -> "$actor nadał(a) Ci rolę moderatora"
		"COMMUNITY_MODERATOR_REVOKED" -> "$actor odebrał(a) Ci rolę moderatora"
		else -> "$actor ma dla Ciebie powiadomienie"
	}
}

@Composable
private fun Footer(isLoading: Boolean, errorMessage: String?, onRetry: () -> Unit) {
	Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
		when {
			isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
			errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text(text = errorMessage, style = MaterialTheme.typography.bodySmall)
				TextButton(onClick = onRetry) { Text("Ponów") }
			}
		}
	}
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		content()
	}
}
