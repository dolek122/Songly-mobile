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
import androidx.compose.ui.unit.dp
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

					item(key = "add") {
						AddFriendSection(
							userId = state.addUserId,
							canSend = state.canSend,
							onChange = viewModel::onAddUserIdChange,
							onSend = viewModel::sendRequest,
						)
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
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun AddFriendSection(
	userId: String,
	canSend: Boolean,
	onChange: (String) -> Unit,
	onSend: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		SectionHeader("Dodaj znajomego")
		OutlinedTextField(
			value = userId,
			onValueChange = onChange,
			label = { Text("ID użytkownika") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth(),
		)
		Button(onClick = onSend, enabled = canSend, modifier = Modifier.align(Alignment.End)) {
			Text("Wyślij zaproszenie")
		}
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
private fun FriendRow(friend: Friend, enabled: Boolean, onRemove: () -> Unit) {
	PersonRow(name = friend.displayName) {
		OutlinedButton(onClick = onRemove, enabled = enabled) { Text("Usuń") }
	}
}

@Composable
private fun PersonRow(name: String, actions: @Composable () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
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
