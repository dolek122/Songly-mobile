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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.ScreenTopBar
import com.songly.android.ui.accessibleAction
import com.songly.shared.model.Notification
import com.songly.shared.util.RelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
	viewModel: NotificationsViewModel,
	onBack: () -> Unit,
	onOpenUserProfile: (String) -> Unit,
	onOpenPost: (postId: String) -> Unit,
	onOpenCommunity: (communityId: String) -> Unit = {},
	onOpenFriends: () -> Unit = {},
	onOpenAdminReports: () -> Unit = {},
	onOpenMailPreferences: () -> Unit = {},
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()

	LaunchedEffect(Unit) {
		viewModel.refresh()
	}

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
		containerColor = Color.Transparent,
		topBar = {
			ScreenTopBar(
				title = if (state.hasUnread) "Powiadomienia (${state.unreadCount})" else "Powiadomienia",
				navigationIcon = { BackButton(onBack) },
				actions = {
					if (state.hasUnread) {
						IconButton(onClick = viewModel::markAllRead) {
							Icon(Icons.Outlined.DoneAll, contentDescription = "Oznacz wszystkie jako przeczytane")
						}
					}
					IconButton(onClick = onOpenMailPreferences) {
						Icon(Icons.Outlined.MailOutline, contentDescription = "Powiadomienia e-mail")
					}
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

				state.errorMessage != null -> EmptyState(icon = Icons.Outlined.CloudOff, message = state.errorMessage) {
					Button(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
				}

				state.isEmpty -> EmptyState(icon = Icons.Outlined.NotificationsNone, message = "Brak powiadomień.")

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
									NotificationTarget.AdminReports -> onOpenAdminReports()
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
			if (isModerationDecision(notification)) {
				Box(
					modifier = Modifier
						.size(40.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.primaryContainer),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = Icons.Outlined.Groups,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
					)
				}
			} else {
				Avatar(
					displayName = notification.actorDisplayName ?: "?",
					avatarUrl = notification.actorAvatarUrl,
					size = 40.dp,
					modifier = Modifier.accessibleAction(
						description = "Profil: ${notification.actorDisplayName ?: "nieznany użytkownik"}",
						onClick = onOpenProfile,
					),
				)
			}
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

internal sealed interface NotificationTarget {
	data class Post(val postId: String) : NotificationTarget
	data class Community(val communityId: String) : NotificationTarget
	data object Friends : NotificationTarget
	data object AdminReports : NotificationTarget
}

internal fun targetOf(notification: Notification): NotificationTarget? = when (notification.type) {
	"POST_LIKED", "POST_COMMENTED", "POST_SHARED", "COMMUNITY_POST_REPORTED", "COMMUNITY_COMMENT_REMOVED" ->
		NotificationTarget.Post(notification.resourceId)

	"COMMUNITY_MEMBER_JOINED", "COMMUNITY_BANNED", "COMMUNITY_POST_REMOVED", "BAN_APPEAL_SUBMITTED",
	"BAN_APPEAL_ACCEPTED", "BAN_APPEAL_REJECTED", "BAN_ESCALATION_ACCEPTED", "BAN_ESCALATION_REJECTED",
	"COMMUNITY_MODERATOR_GRANTED", "COMMUNITY_MODERATOR_REVOKED",
	-> NotificationTarget.Community(notification.resourceId)

	"FRIEND_REQUEST_RECEIVED" -> NotificationTarget.Friends

	"POST_REPORTED" -> NotificationTarget.AdminReports

	else -> null
}

internal val MODERATION_DECISIONS = setOf(
	"COMMUNITY_BANNED", "COMMUNITY_POST_REMOVED", "COMMUNITY_COMMENT_REMOVED", "POST_REPORT_DISMISSED",
	"BAN_APPEAL_ACCEPTED", "BAN_APPEAL_REJECTED", "BAN_ESCALATION_ACCEPTED", "BAN_ESCALATION_REJECTED", "POST_REMOVED",
)

internal fun isModerationDecision(notification: Notification): Boolean = notification.type in MODERATION_DECISIONS

internal fun notificationText(notification: Notification): String {
	val actor = notification.actorDisplayName ?: "Ktoś"
	return when (notification.type) {
		"POST_LIKED" -> "$actor polubił(a) Twój post"
		"POST_COMMENTED" -> "$actor skomentował(a) Twój post"
		"POST_SHARED" -> "$actor udostępnił(a) Twój post"
		"FRIEND_REQUEST_RECEIVED" -> "$actor wysłał(a) Ci zaproszenie do znajomych"
		"FRIEND_REQUEST_ACCEPTED" -> "$actor zaakceptował(a) Twoje zaproszenie do znajomych"
		"COMMUNITY_MEMBER_JOINED" -> "$actor dołączył(a) do Twojej społeczności"
		"COMMUNITY_BANNED" -> "Moderacja zbanowała Cię w społeczności"
		"COMMUNITY_POST_REMOVED" -> "Moderacja usunęła Twój post w społeczności"
		"COMMUNITY_COMMENT_REMOVED" -> "Moderacja usunęła Twój komentarz w społeczności"
		"COMMUNITY_POST_REPORTED" -> "Nowe zgłoszenie posta do rozpatrzenia"
		"POST_REPORT_DISMISSED" -> "Moderacja odrzuciła Twoje zgłoszenie"
		"POST_REPORTED" -> "Nowe zgłoszenie posta do rozpatrzenia przez administrację"
		"POST_REMOVED" -> "Administracja usunęła Twój post"
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
