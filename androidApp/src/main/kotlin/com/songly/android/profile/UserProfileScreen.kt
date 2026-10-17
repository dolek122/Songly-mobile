package com.songly.android.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.songly.android.post.PostListDialogs
import com.songly.android.post.PostListViewModel
import com.songly.android.post.postListItems
import com.songly.android.ui.Avatar

/**
 * Ekran cudzego profilu (read-only, §14 natywny UI): avatar, nazwa, bio i ulubione
 * gatunki innego użytkownika. Dane z [UserProfileViewModel]; akcje relacji
 * (zaproszenie/blokada) żyją na razie w ekranie znajomych i dopasowań.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(
	viewModel: UserProfileViewModel,
	postsViewModel: PostListViewModel,
	onBack: () -> Unit,
	onOpenComments: (postId: String) -> Unit,
	/** Oglądający jest administratorem platformy — pokazujemy akcje admina. */
	isAdminViewer: Boolean = false,
	onOpenModerationHistory: (userId: String) -> Unit = {},
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
		postsViewModel.refresh()
	}

	PostListDialogs(postsViewModel)

	if (state.showSuspendDialog) {
		SuspendDialog(state, viewModel)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(state.profile?.displayName ?: "Profil") },
				navigationIcon = {
					TextButton(onClick = onBack) { Text("Wróć") }
				},
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
					Text(state.errorMessage, textAlign = TextAlign.Center)
					Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				state.profile != null -> LazyColumn(
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item(key = "profile") { ProfileContent(state, onInvite = viewModel::invite) }
					if (isAdminViewer && !state.isSelf) {
						item(key = "admin-actions") {
							AdminActions(
								state = state,
								onSuspend = viewModel::openSuspendDialog,
								onGrantAdmin = viewModel::grantAdmin,
								onHistory = { state.profile?.userId?.let(onOpenModerationHistory) },
							)
						}
					}
					item(key = "posts-title") {
						Text("Posty", style = MaterialTheme.typography.titleMedium)
					}
					postListItems(
						viewModel = postsViewModel,
						emptyText = "Ten użytkownik nie opublikował jeszcze żadnego posta.",
						onOpenComments = onOpenComments,
						// Autor każdego posta to właściciel tego profilu — jesteśmy już u niego.
						onOpenAuthor = {},
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(state: UserProfileUiState, onInvite: () -> Unit) {
	val profile = state.profile ?: return
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Avatar(
			displayName = profile.displayName,
			avatarUrl = profile.avatarUrl,
			size = 96.dp,
		)
		Text(
			text = profile.displayName,
			style = MaterialTheme.typography.headlineSmall,
		)
		if (!profile.bio.isNullOrBlank()) {
			Text(
				text = profile.bio!!,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
			)
		}

		// Zaproszenie do znajomych — nie na własnym profilu. Status znajomości nie jest
		// znany z góry, więc ewentualne odrzucenie pokazujemy po kliknięciu.
		if (!state.isSelf) {
			if (state.inviteSent) {
				Text(
					text = "Zaproszenie wysłane",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				Button(onClick = onInvite, enabled = !state.isInviting) {
					Text(if (state.isInviting) "Wysyłanie…" else "Wyślij zaproszenie")
				}
				if (state.inviteMessage != null) {
					Text(
						text = state.inviteMessage,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.error,
						textAlign = TextAlign.Center,
					)
				}
			}
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text("Ulubione gatunki", style = MaterialTheme.typography.titleMedium)
			if (profile.favoriteGenres.isEmpty()) {
				Text(
					text = "Ten użytkownik nie wybrał jeszcze gatunków.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					profile.favoriteGenres.forEach { genre ->
						AssistChip(onClick = {}, label = { Text(genre.name) })
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminActions(
	state: UserProfileUiState,
	onSuspend: () -> Unit,
	onGrantAdmin: () -> Unit,
	onHistory: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text("Administracja", style = MaterialTheme.typography.titleSmall)
			if (state.adminMessage != null) {
				Text(state.adminMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
			}
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedButton(
					onClick = onSuspend,
					enabled = !state.isAdminActionRunning,
					colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
				) { Text("Zawieś konto") }
				OutlinedButton(onClick = onGrantAdmin, enabled = !state.isAdminActionRunning) { Text("Nadaj rolę admina") }
				TextButton(onClick = onHistory) { Text("Historia moderacji") }
			}
		}
	}
}

@Composable
private fun SuspendDialog(state: UserProfileUiState, viewModel: UserProfileViewModel) {
	AlertDialog(
		onDismissRequest = viewModel::dismissSuspendDialog,
		title = { Text("Zawiesić konto?") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					"Zawieszenie odcina od całej aplikacji i ukrywa treści tej osoby. Powód zobaczy zawieszony.",
					style = MaterialTheme.typography.bodySmall,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					listOf<Int?>(1, 7, 30, null).forEach { days ->
						FilterChip(
							selected = state.suspendDays == days,
							onClick = { viewModel.onSuspendDaysChange(days) },
							label = { Text(if (days == null) "Na zawsze" else "$days d") },
						)
					}
				}
				OutlinedTextField(
					value = state.suspendReason,
					onValueChange = viewModel::onSuspendReasonChange,
					label = { Text("Powód (wymagany)") },
					enabled = !state.isAdminActionRunning,
					minLines = 2,
					isError = state.suspendReason.length > 500,
					modifier = Modifier.fillMaxWidth(),
				)
				if (state.suspendError != null) {
					Text(state.suspendError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = viewModel::suspend,
				enabled = state.canSuspend,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) { Text(if (state.isAdminActionRunning) "Zawieszanie…" else "Zawieś") }
		},
		dismissButton = {
			TextButton(onClick = viewModel::dismissSuspendDialog, enabled = !state.isAdminActionRunning) { Text("Anuluj") }
		},
	)
}
