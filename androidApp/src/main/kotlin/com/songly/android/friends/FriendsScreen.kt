package com.songly.android.friends

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.NavigationRow
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Friend
import com.songly.shared.paging.PagedSection
import com.songly.shared.model.FriendRequest

/**
 * Ekran znajomych (Compose, natywny UI — §14): lista znajomych, zaproszenia
 * otrzymane (akceptuj/odrzuć) i wysłane (anuluj) oraz wysyłka zaproszenia po ID.
 * Stan z [FriendsViewModel]; dane z modułu social-graph przez `shared`.
 *
 * Ekran bywa zakładką dolnego paska ([onBack] `null`, bez strzałki) albo
 * otwiera się nad powiadomieniami, gdy ktoś przyszedł przyjąć zaproszenie.
 * [onOpenMatches] dokłada na górze wejście do dopasowanych — tam szuka się
 * nowych znajomych o podobnym guście.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
	viewModel: FriendsViewModel,
	onOpenUserProfile: (String) -> Unit,
	onBack: (() -> Unit)? = null,
	onOpenMatches: (() -> Unit)? = null,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Znajomi") },
				navigationIcon = { if (onBack != null) BackButton(onBack) },
			)
		},
	) { padding ->
		PullToRefreshBox(
			isRefreshing = state.isRefreshing,
			onRefresh = viewModel::pullToRefresh,
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
			when {
				state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> EmptyState(icon = Icons.Outlined.CloudOff, message = state.errorMessage) {
					Button(onClick = viewModel::load) { Text("Spróbuj ponownie") }
				}

				else -> LazyColumn(
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					if (state.statusMessage != null) {
						item(key = "status") {
							Text(
								text = state.statusMessage,
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.primary,
							)
						}
					}

					if (onOpenMatches != null) {
						item(key = "matches") {
							NavigationRow(
								icon = Icons.Outlined.AutoAwesome,
								title = "Dopasowani",
								subtitle = "Osoby o podobnym guście muzycznym",
								onClick = onOpenMatches,
							)
						}
					}

					item(key = "search") {
						SearchSection(
							query = state.searchQuery,
							isSearching = state.isSearching,
							canSearch = state.canSearch,
							onChange = viewModel::onSearchQueryChange,
							onSearch = viewModel::search,
						)
					}
					if (state.searchResults.isNotEmpty()) {
						items(state.searchResults, key = { "sr-${it.userId}" }) { result ->
							SearchResultRow(
								name = result.displayName,
								avatarUrl = result.avatarUrl,
								enabled = !state.isBusy,
								onSend = { viewModel.sendRequestTo(result.userId) },
								onBlock = { viewModel.block(result.userId) },
								onOpenProfile = { onOpenUserProfile(result.userId) },
							)
						}
					} else if (state.searched && !state.isSearching) {
						item(key = "sr-empty") { EmptyHint("Brak użytkowników dla tej frazy.") }
					}

					item(key = "h-incoming") { SectionHeader("Zaproszenia otrzymane") }
					if (state.incoming.items.isEmpty()) {
						item(key = "e-incoming") { EmptyHint("Brak nowych zaproszeń.") }
					} else {
						items(state.incoming.items, key = { "in-${it.id}" }) { request ->
							IncomingRequestRow(
								request = request,
								enabled = !state.isBusy,
								onAccept = { viewModel.accept(request.id) },
								onDecline = { viewModel.decline(request.id) },
								onOpenProfile = { onOpenUserProfile(request.requesterId) },
							)
						}
					}

					item(key = "f-incoming") {
						SectionFooter(state.incoming, viewModel::loadMoreIncoming, viewModel::retryMoreIncoming)
					}

					item(key = "h-outgoing") { SectionHeader("Zaproszenia wysłane") }
					if (state.outgoing.items.isEmpty()) {
						item(key = "e-outgoing") { EmptyHint("Brak wysłanych zaproszeń.") }
					} else {
						items(state.outgoing.items, key = { "out-${it.id}" }) { request ->
							OutgoingRequestRow(
								request = request,
								enabled = !state.isBusy,
								onCancel = { viewModel.cancel(request.id) },
								onOpenProfile = { onOpenUserProfile(request.addresseeId) },
							)
						}
					}

					item(key = "f-outgoing") {
						SectionFooter(state.outgoing, viewModel::loadMoreOutgoing, viewModel::retryMoreOutgoing)
					}

					item(key = "h-friends") { SectionHeader("Znajomi") }
					if (state.friends.items.isEmpty()) {
						item(key = "e-friends") { EmptyHint("Nie masz jeszcze znajomych.") }
					} else {
						items(state.friends.items, key = { "fr-${it.userId}" }) { friend ->
							FriendRow(
								friend = friend,
								enabled = !state.isBusy,
								onRemove = { viewModel.removeFriend(friend.userId) },
								onBlock = { viewModel.block(friend.userId) },
								onOpenProfile = { onOpenUserProfile(friend.userId) },
							)
						}
					}

					item(key = "f-friends") {
						SectionFooter(state.friends, viewModel::loadMoreFriends, viewModel::retryMoreFriends)
					}

					item(key = "h-blocked") { SectionHeader("Zablokowani") }
					if (state.blocked.items.isEmpty()) {
						item(key = "e-blocked") { EmptyHint("Nie masz zablokowanych użytkowników.") }
					} else {
						items(state.blocked.items, key = { "bl-${it.userId}" }) { blocked ->
							BlockedRow(
								blocked = blocked,
								enabled = !state.isBusy,
								onUnblock = { viewModel.unblock(blocked.userId) },
							)
						}
					}

					item(key = "f-blocked") {
						SectionFooter(state.blocked, viewModel::loadMoreBlocked, viewModel::retryMoreBlocked)
					}
				}
			}
		}
	}
}

@Composable
private fun SearchSection(
	query: String,
	isSearching: Boolean,
	canSearch: Boolean,
	onChange: (String) -> Unit,
	onSearch: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		SectionHeader("Znajdź znajomych")
		OutlinedTextField(
			value = query,
			onValueChange = onChange,
			label = { Text("Szukaj po nazwie") },
			leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
			singleLine = true,
			enabled = !isSearching,
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
			keyboardActions = KeyboardActions(onSearch = { onSearch() }),
			modifier = Modifier.fillMaxWidth(),
		)
		Button(onClick = onSearch, enabled = canSearch, modifier = Modifier.align(Alignment.End)) {
			Text(if (isSearching) "Szukanie…" else "Szukaj")
		}
	}
}

@Composable
private fun SearchResultRow(
	name: String,
	avatarUrl: String?,
	enabled: Boolean,
	onSend: () -> Unit,
	onBlock: () -> Unit,
	onOpenProfile: () -> Unit,
) {
	PersonRow(
		name = name,
		leading = { Avatar(displayName = name, avatarUrl = avatarUrl, size = 44.dp) },
		onClick = onOpenProfile,
	) {
		Button(onClick = onSend, enabled = enabled) { Text("Zaproś") }
		MoreActions(name = name, enabled = enabled, actions = listOf("Zablokuj" to onBlock))
	}
}

@Composable
private fun IncomingRequestRow(
	request: FriendRequest,
	enabled: Boolean,
	onAccept: () -> Unit,
	onDecline: () -> Unit,
	onOpenProfile: () -> Unit,
) {
	PersonRow(
		name = request.requesterDisplayName,
		leading = {
			Avatar(displayName = request.requesterDisplayName, avatarUrl = request.requesterAvatarUrl, size = 44.dp)
		},
		onClick = onOpenProfile,
	) {
		Button(onClick = onAccept, enabled = enabled) { Text("Akceptuj") }
		OutlinedButton(onClick = onDecline, enabled = enabled) { Text("Odrzuć") }
	}
}

@Composable
private fun OutgoingRequestRow(
	request: FriendRequest,
	enabled: Boolean,
	onCancel: () -> Unit,
	onOpenProfile: () -> Unit,
) {
	PersonRow(
		name = request.addresseeDisplayName,
		leading = {
			Avatar(displayName = request.addresseeDisplayName, avatarUrl = request.addresseeAvatarUrl, size = 44.dp)
		},
		onClick = onOpenProfile,
	) {
		OutlinedButton(onClick = onCancel, enabled = enabled) { Text("Anuluj") }
	}
}

@Composable
private fun FriendRow(
	friend: Friend,
	enabled: Boolean,
	onRemove: () -> Unit,
	onBlock: () -> Unit,
	onOpenProfile: () -> Unit,
) {
	PersonRow(
		name = friend.displayName,
		leading = { Avatar(displayName = friend.displayName, avatarUrl = friend.avatarUrl, size = 44.dp) },
		onClick = onOpenProfile,
	) {
		MoreActions(
			name = friend.displayName,
			enabled = enabled,
			actions = listOf("Usuń ze znajomych" to onRemove, "Zablokuj" to onBlock),
		)
	}
}

@Composable
private fun BlockedRow(blocked: BlockedUser, enabled: Boolean, onUnblock: () -> Unit) {
	PersonRow(name = blocked.displayName) {
		OutlinedButton(onClick = onUnblock, enabled = enabled) { Text("Odblokuj") }
	}
}

@Composable
private fun PersonRow(
	name: String,
	leading: (@Composable () -> Unit)? = null,
	onClick: (() -> Unit)? = null,
	actions: @Composable () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// Avatar + nazwa prowadzą do profilu osoby (gdy onClick ustawiony); akcje osobno.
		Row(
			modifier = Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			leading?.invoke()
			Text(
				text = name,
				style = MaterialTheme.typography.titleMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		actions()
	}
}

/**
 * Rzadkie akcje na osobie (usuń, zablokuj) schowane pod „⋯" — w wierszu zostaje
 * jedna rzecz, którą robi się najczęściej, więc lista nie jest ścianą przycisków.
 */
@Composable
private fun MoreActions(name: String, enabled: Boolean, actions: List<Pair<String, () -> Unit>>) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton(onClick = { expanded = true }, enabled = enabled) {
			Icon(Icons.Outlined.MoreVert, contentDescription = "Więcej akcji: $name")
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			actions.forEach { (label, action) ->
				DropdownMenuItem(
					text = { Text(label) },
					onClick = {
						expanded = false
						action()
					},
				)
			}
		}
	}
}

@Composable
private fun SectionHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleLarge,
		modifier = Modifier.padding(top = 12.dp),
	)
}

@Composable
private fun EmptyHint(text: String) {
	Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Stopka sekcji: „Pokaż więcej", kręciołek albo błąd z ponowieniem. Nic nie
 * renderuje, gdy nie ma czego doczytywać.
 *
 * Doczytywanie na przycisk, nie na przewinięcie: cztery sekcje dzielą jedną
 * przewijaną listę, więc automat nie miałby jak zgadnąć, którą z nich użytkownik
 * właśnie czyta — i po dojechaniu na dół dobierałby strony do wszystkich naraz.
 */
@Composable
private fun <T> SectionFooter(section: PagedSection<T>, onLoadMore: () -> Unit, onRetry: () -> Unit) {
	// Lokalna kopia, bo pole z innego modułu nie podlega smart castowi.
	val appendError = section.appendError
	when {
		appendError != null -> Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = appendError,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.weight(1f),
			)
			TextButton(onClick = onRetry) { Text("Ponów") }
		}

		section.isLoadingMore -> Box(modifier = Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
			CircularProgressIndicator(modifier = Modifier.size(20.dp))
		}

		!section.endReached -> TextButton(onClick = onLoadMore) { Text("Pokaż więcej") }
	}
}
