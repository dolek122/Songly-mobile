package com.songly.android.communities

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.shared.model.Community

/**
 * Ekran społeczności (Compose, natywny UI — §14): lista wszystkich społeczności
 * z dołączaniem/opuszczaniem i tworzeniem nowej (FAB → dialog). Klik w kartę
 * otwiera szczegóły. Stan z [CommunitiesViewModel]; dane z modułu community.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
	viewModel: CommunitiesViewModel,
	onBack: () -> Unit,
	onOpenCommunity: (String) -> Unit,
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

	if (state.showCreateDialog) {
		CreateCommunityDialog(
			name = state.createName,
			description = state.createDescription,
			isCreating = state.isCreating,
			canCreate = state.canCreate,
			errorMessage = state.createError,
			onNameChange = viewModel::onCreateNameChange,
			onDescriptionChange = viewModel::onCreateDescriptionChange,
			onConfirm = viewModel::confirmCreate,
			onDismiss = viewModel::dismissCreateDialog,
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Społeczności") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
			)
		},
		floatingActionButton = {
			FloatingActionButton(onClick = viewModel::openCreateDialog) {
				Text(text = "+", style = MaterialTheme.typography.headlineMedium)
			}
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading && state.communities.isEmpty() ->
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> CenteredMessage {
					Text(state.errorMessage)
					Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				state.isEmpty -> CenteredMessage {
					Text("Brak społeczności. Załóż pierwszą!", style = MaterialTheme.typography.bodyMedium)
				}

				else -> LazyColumn(
					state = listState,
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
					items(state.communities, key = { it.id }) { community ->
						CommunityCard(
							community = community,
							onToggleMembership = { viewModel.toggleMembership(community) },
							onOpen = { onOpenCommunity(community.id) },
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
private fun CommunityCard(community: Community, onToggleMembership: () -> Unit, onOpen: () -> Unit) {
	Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = community.name,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
				)
				if (!community.description.isNullOrBlank()) {
					Text(
						text = community.description!!,
						style = MaterialTheme.typography.bodyMedium,
						modifier = Modifier.padding(top = 2.dp),
					)
				}
				Text(
					text = "${community.memberCount} członków",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 4.dp),
				)
			}
			// Właściciel nie może opuścić społeczności — nie pokazujemy mu przycisku.
			when {
				community.isOwner -> Text(
					text = "Właściciel",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary,
				)

				community.isMember -> OutlinedButton(onClick = onToggleMembership) { Text("Opuść") }
				else -> Button(onClick = onToggleMembership) { Text("Dołącz") }
			}
		}
	}
}

@Composable
private fun CreateCommunityDialog(
	name: String,
	description: String,
	isCreating: Boolean,
	canCreate: Boolean,
	errorMessage: String?,
	onNameChange: (String) -> Unit,
	onDescriptionChange: (String) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Nowa społeczność") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = onNameChange,
					label = { Text("Nazwa") },
					singleLine = true,
					enabled = !isCreating,
					isError = errorMessage != null,
					modifier = Modifier.fillMaxWidth(),
				)
				OutlinedTextField(
					value = description,
					onValueChange = onDescriptionChange,
					label = { Text("Opis (opcjonalny)") },
					enabled = !isCreating,
					minLines = 2,
					modifier = Modifier.fillMaxWidth(),
				)
				if (errorMessage != null) {
					Text(
						text = errorMessage,
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onConfirm, enabled = canCreate) {
				Text(if (isCreating) "Tworzenie…" else "Utwórz")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Anuluj") }
		},
	)
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
