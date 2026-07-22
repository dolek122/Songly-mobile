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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
				},
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
							onClick = { viewModel.markRead(notification) },
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
private fun NotificationCard(notification: Notification, onClick: () -> Unit) {
	val unread = notification.isUnread
	Card(
		modifier = Modifier.fillMaxWidth().clickable(enabled = unread, onClick = onClick),
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

/** Mapuje typ powiadomienia + nazwę autora na zdanie po polsku. */
private fun notificationText(notification: Notification): String {
	val actor = notification.actorDisplayName ?: "Ktoś"
	val action = when (notification.type) {
		"POST_LIKED" -> "polubił(a) Twój post"
		"POST_COMMENTED" -> "skomentował(a) Twój post"
		"POST_SHARED" -> "udostępnił(a) Twój post"
		"FRIEND_REQUEST_RECEIVED" -> "wysłał(a) Ci zaproszenie do znajomych"
		"FRIEND_REQUEST_ACCEPTED" -> "zaakceptował(a) Twoje zaproszenie do znajomych"
		"COMMUNITY_MEMBER_JOINED" -> "dołączył(a) do Twojej społeczności"
		else -> "ma dla Ciebie powiadomienie"
	}
	return "$actor $action"
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
