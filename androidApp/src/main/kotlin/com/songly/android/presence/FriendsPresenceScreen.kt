package com.songly.android.presence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.ScreenTopBar
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.presence.PresenceProblem

/**
 * „Znajomi teraz słuchają" na żywo (ADR 0007 backendu, Etap 3): stan początkowy z
 * REST-a, potem zmiany pushowane strumieniem SSE. Ekran nie odpytuje serwera sam —
 * całość (podłączenie, reconnect, scalanie zmian) siedzi w module `shared`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsPresenceScreen(
	viewModel: FriendsPresenceViewModel,
	onBack: () -> Unit,
	onOpenUserProfile: (String) -> Unit,
) {
	val state = viewModel.uiState

	// Strumień żyje tak długo, jak ekran — zejście z niego zwalnia połączenie.
	DisposableEffect(Unit) {
		viewModel.start()
		onDispose { viewModel.stop() }
	}

	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			ScreenTopBar(
				title = "Znajomi teraz",
				navigationIcon = { BackButton(onBack) },
				actions = {
					ConnectionLabel(live = state.live, connecting = state.connecting, problem = state.problem)
				},
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			if (state.friends.isEmpty()) {
				EmptyState(
					icon = Icons.Outlined.Headphones,
					message = when {
						state.problem == PresenceProblem.TOO_MANY_STREAMS -> STREAM_LIMIT_MESSAGE
						state.connecting -> "Łączenie ze strumieniem…"
						else -> "Nikt ze znajomych nic teraz nie gra."
					},
				)
			} else {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					items(state.friends, key = { it.userId }) { friend ->
						FriendPresenceCard(
							friend = friend,
							onClick = { onOpenUserProfile(friend.userId) },
						)
					}
				}
			}
		}
	}
}

/**
 * Rozróżnienie „na żywo" od „ostatnio znane" jest tu istotne: bez niego user
 * widziałby nieświeże statusy nie wiedząc, że strumień padł.
 */
@Composable
private fun ConnectionLabel(live: Boolean, connecting: Boolean, problem: PresenceProblem?) {
	val (label, color) = when {
		live -> "na żywo" to MaterialTheme.colorScheme.primary
		problem == PresenceProblem.TOO_MANY_STREAMS -> "zajęte" to MaterialTheme.colorScheme.error
		connecting -> "łączenie…" to MaterialTheme.colorScheme.onSurfaceVariant
		else -> "offline" to MaterialTheme.colorScheme.error
	}
	// Kropka powtarza kolorem to, co mówi napis — sama barwa nie niesie informacji.
	Row(
		modifier = Modifier.padding(end = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
		Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
	}
}

@Composable
private fun FriendPresenceCard(friend: FriendNowPlaying, onClick: () -> Unit) {
	val status = friend.status ?: return
	Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Avatar(displayName = friend.displayName ?: "?", avatarUrl = friend.avatarUrl, size = 48.dp)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = friend.displayName ?: "Użytkownik",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
				)
				Text(
					text = listOfNotNull(status.artist, status.title).joinToString(" — "),
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = if (status.playing) status.providerLabel else "${status.providerLabel} · wstrzymane",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			// Zielony korektor przy tym, co gra właśnie teraz (ozdoba — stan mówi linijka wyżej).
			if (status.playing) {
				Icon(
					Icons.Filled.GraphicEq,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
				)
			}
		}
	}
}

// Limit strumieni na użytkownika egzekwuje backend (429 TOO_MANY_STREAMS) — zwykle
// biorą się z porzuconych sesji na innych urządzeniach, które wygasają same.
private const val STREAM_LIMIT_MESSAGE =
	"Masz otwarte strumienie na innych urządzeniach. Ten wróci, gdy któryś z nich wygaśnie."
