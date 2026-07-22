package com.songly.android.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendRequest

/**
 * Ekran znajomych (Compose, natywny UI — §14): lista znajomych, zaproszenia
 * otrzymane (akceptuj/odrzuć) i wysłane (anuluj) oraz wysyłka zaproszenia po ID.
 * Stan z [FriendsViewModel]; dane z modułu social-graph przez `shared`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
	viewModel: FriendsViewModel,
	onBack: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Znajomi") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> Column(
					modifier = Modifier.fillMaxSize().padding(24.dp),
					verticalArrangement = Arrangement.Center,
					horizontalAlignment = Alignment.CenterHorizontally,
				) {
					Text(state.errorMessage)
					Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
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
							)
						}
					} else if (state.searched && !state.isSearching) {
						item(key = "sr-empty") { EmptyHint("Brak użytkowników dla tej frazy.") }
					}

					item(key = "h-incoming") { SectionHeader("Zaproszenia otrzymane") }
					if (state.incoming.isEmpty()) {
						item(key = "e-incoming") { EmptyHint("Brak nowych zaproszeń.") }
					} else {
						items(state.incoming, key = { "in-${it.id}" }) { request ->
							IncomingRequestRow(
								request = request,
								enabled = !state.isBusy,
								onAccept = { viewModel.accept(request.id) },
								onDecline = { viewModel.decline(request.id) },
							)
						}
					}

					item(key = "h-outgoing") { SectionHeader("Zaproszenia wysłane") }
					if (state.outgoing.isEmpty()) {
						item(key = "e-outgoing") { EmptyHint("Brak wysłanych zaproszeń.") }
					} else {
						items(state.outgoing, key = { "out-${it.id}" }) { request ->
							OutgoingRequestRow(
								request = request,
								enabled = !state.isBusy,
								onCancel = { viewModel.cancel(request.id) },
							)
						}
					}

					item(key = "h-friends") { SectionHeader("Znajomi") }
					if (state.friends.isEmpty()) {
						item(key = "e-friends") { EmptyHint("Nie masz jeszcze znajomych.") }
					} else {
						items(state.friends, key = { "fr-${it.userId}" }) { friend ->
							FriendRow(
								friend = friend,
								enabled = !state.isBusy,
								onRemove = { viewModel.removeFriend(friend.userId) },
								onBlock = { viewModel.block(friend.userId) },
							)
						}
					}

					item(key = "h-blocked") { SectionHeader("Zablokowani") }
					if (state.blocked.isEmpty()) {
						item(key = "e-blocked") { EmptyHint("Nie masz zablokowanych użytkowników.") }
					} else {
						items(state.blocked, key = { "bl-${it.userId}" }) { blocked ->
							BlockedRow(
								blocked = blocked,
								enabled = !state.isBusy,
								onUnblock = { viewModel.unblock(blocked.userId) },
							)
						}
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
) {
	PersonRow(
		name = name,
		leading = { Avatar(displayName = name, avatarUrl = avatarUrl, size = 36.dp) },
	) {
		Button(onClick = onSend, enabled = enabled) { Text("Zaproś") }
		OutlinedButton(onClick = onBlock, enabled = enabled) { Text("Blokuj") }
	}
}

@Composable
private fun IncomingRequestRow(
	request: FriendRequest,
	enabled: Boolean,
	onAccept: () -> Unit,
	onDecline: () -> Unit,
) {
	PersonRow(name = request.requesterDisplayName) {
		Button(onClick = onAccept, enabled = enabled) { Text("Akceptuj") }
		OutlinedButton(onClick = onDecline, enabled = enabled) { Text("Odrzuć") }
	}
}

@Composable
private fun OutgoingRequestRow(request: FriendRequest, enabled: Boolean, onCancel: () -> Unit) {
	PersonRow(name = request.addresseeDisplayName) {
		OutlinedButton(onClick = onCancel, enabled = enabled) { Text("Anuluj") }
	}
}

@Composable
private fun FriendRow(friend: Friend, enabled: Boolean, onRemove: () -> Unit, onBlock: () -> Unit) {
	PersonRow(name = friend.displayName) {
		OutlinedButton(onClick = onRemove, enabled = enabled) { Text("Usuń") }
		OutlinedButton(onClick = onBlock, enabled = enabled) { Text("Blokuj") }
	}
}

@Composable
private fun BlockedRow(blocked: BlockedUser, enabled: Boolean, onUnblock: () -> Unit) {
	PersonRow(name = blocked.displayName) {
		OutlinedButton(onClick = onUnblock, enabled = enabled) { Text("Odblokuj") }
	}
}

@Composable
private fun PersonRow(name: String, leading: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		leading?.invoke()
		Text(
			text = name,
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.weight(1f),
		)
		actions()
	}
}

@Composable
private fun SectionHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleMedium,
		fontWeight = FontWeight.SemiBold,
		modifier = Modifier.padding(top = 8.dp),
	)
}

@Composable
private fun EmptyHint(text: String) {
	Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
