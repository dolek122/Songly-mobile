package com.songly.android.communities

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.FilterPill
import com.songly.android.ui.SonglyFab
import com.songly.android.ui.TabTopBar
import com.songly.android.ui.TeardropShape
import com.songly.shared.model.Community

/**
 * Ekran społeczności (Compose, natywny UI — §14): lista wszystkich społeczności
 * z dołączaniem/opuszczaniem i tworzeniem nowej (FAB → dialog). Klik w kartę
 * otwiera szczegóły. Stan z [CommunitiesViewModel]; dane z modułu community.
 *
 * Jako zakładka dolnego paska [onBack] jest `null` — bez strzałki wstecz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
	viewModel: CommunitiesViewModel,
	onOpenCommunity: (String) -> Unit,
	onOpenUserProfile: (String) -> Unit,
	onBack: (() -> Unit)? = null,
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
		containerColor = Color.Transparent,
		topBar = {
			TabTopBar(
				title = "Społeczności",
				navigationIcon = { if (onBack != null) BackButton(onBack) },
			)
		},
		floatingActionButton = {
			SonglyFab(icon = Icons.Filled.Add, contentDescription = "Nowa społeczność", onClick = viewModel::openCreateDialog)
		},
	) { padding ->
		Column(modifier = Modifier.fillMaxSize().padding(padding)) {
			Row(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				FilterPill(
					label = "Wszystkie",
					selected = state.tab == CommunitiesTab.ALL,
					onClick = { viewModel.selectTab(CommunitiesTab.ALL) },
				)
				FilterPill(
					label = "Moje",
					selected = state.tab == CommunitiesTab.MINE,
					onClick = { viewModel.selectTab(CommunitiesTab.MINE) },
				)
			}
			PullToRefreshBox(
				isRefreshing = state.isRefreshing,
				onRefresh = viewModel::pullToRefresh,
				modifier = Modifier.fillMaxSize(),
			) {
				when {
				state.isLoading && state.communities.isEmpty() ->
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> EmptyState(icon = Icons.Outlined.CloudOff, message = state.errorMessage) {
					Button(onClick = viewModel::refresh) { Text("Spróbuj ponownie") }
				}

				state.isEmpty -> EmptyState(
					icon = Icons.Outlined.Groups,
					message = if (state.tab == CommunitiesTab.MINE) {
						"Nie należysz jeszcze do żadnej społeczności."
					} else {
						"Brak społeczności. Załóż pierwszą!"
					},
				)

				else -> LazyColumn(
					state = listState,
					// Dół z zapasem na przycisk „Nowa społeczność".
					contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
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
							onOpenOwner = { onOpenUserProfile(community.ownerId) },
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
}

@Composable
private fun CommunityCard(
	community: Community,
	onToggleMembership: () -> Unit,
	onOpen: () -> Unit,
	onOpenOwner: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			CommunityTile(name = community.name)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = community.name,
					style = MaterialTheme.typography.titleMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (!community.description.isNullOrBlank()) {
					Text(
						text = community.description!!,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.padding(top = 2.dp),
					)
				}
				Text(
					text = membersLabel(community.memberCount),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 4.dp),
				)
				// Założyciel — osobny tap-target nad kliknięciem karty, prowadzi do jego profilu.
				if (community.ownerDisplayName != null) {
					Row(
						modifier = Modifier.padding(top = 6.dp).clickable(onClick = onOpenOwner),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Avatar(
							displayName = community.ownerDisplayName!!,
							avatarUrl = community.ownerAvatarUrl,
							size = 24.dp,
						)
						Text(
							text = "Założyciel: ${community.ownerDisplayName}",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
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

/**
 * Kafelek społeczności: kropla z pierwszą literą nazwy. Społeczności nie mają
 * okładek, a sama lista nazw czytała się jak spis treści — kafelek daje każdej
 * karcie punkt zaczepienia dla oka.
 */
@Composable
internal fun CommunityTile(name: String, size: Dp = 52.dp, textStyle: TextStyle = MaterialTheme.typography.titleLarge) {
	Box(
		modifier = Modifier
			.size(size)
			.clip(TeardropShape)
			.background(MaterialTheme.colorScheme.primaryContainer),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = name.trim().firstOrNull()?.uppercase() ?: "?",
			style = textStyle,
			color = MaterialTheme.colorScheme.onPrimaryContainer,
		)
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

/** „1 członek", ale „2 członków", „5 członków" — rzeczownik męskoosobowy, więc odmienia się tylko jedynka. */
internal fun membersLabel(count: Long): String =
	if (count == 1L) "1 członek" else "$count członków"
