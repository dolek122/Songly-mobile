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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.songly.android.ui.Avatar
import com.songly.shared.model.Community
import com.songly.shared.model.BanDetails
import com.songly.shared.model.CommunityMember
import com.songly.android.post.PostListDialogs
import com.songly.android.post.PostListViewModel
import com.songly.android.post.postListItems
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ekran szczegółów społeczności (Compose, natywny UI — §14): nagłówek z opisem i
 * akcją dołącz/opuść oraz lista członków (paginacja). Stan z [CommunityDetailViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
	viewModel: CommunityDetailViewModel,
	postsViewModel: PostListViewModel,
	onOpenComments: (postId: String) -> Unit,
	onBack: () -> Unit,
	onDeleted: () -> Unit,
	onOpenUserProfile: (String) -> Unit,
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()

	LaunchedEffect(Unit) {
		viewModel.load()
		postsViewModel.refresh()
	}

	PostListDialogs(postsViewModel)

	// Po usunięciu społeczności wróć do listy (i pozwól jej się odświeżyć).
	LaunchedEffect(state.deleted) {
		if (state.deleted) onDeleted()
	}

	if (state.showEditDialog) {
		EditCommunityDialog(
			name = state.editName,
			description = state.editDescription,
			isSaving = state.isSaving,
			canSave = state.canSaveEdit,
			errorMessage = state.editError,
			onNameChange = viewModel::onEditNameChange,
			onDescriptionChange = viewModel::onEditDescriptionChange,
			onConfirm = viewModel::confirmEdit,
			onDismiss = viewModel::dismissEditDialog,
		)
	}

	if (state.showDeleteDialog) {
		DeleteCommunityDialog(
			isDeleting = state.isDeleting,
			errorMessage = state.deleteError,
			onConfirm = viewModel::confirmDelete,
			onDismiss = viewModel::dismissDeleteDialog,
		)
	}

	val shouldLoadMore by remember {
		derivedStateOf {
			val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			val total = listState.layoutInfo.totalItemsCount
			total > 0 && lastVisible >= total - 3
		}
	}
	// Automatyczne doczytywanie tylko dla członków — posty mają przycisk (patrz postListItems).
	LaunchedEffect(shouldLoadMore, state.selectedTab) {
		if (shouldLoadMore && state.selectedTab == CommunityTab.MEMBERS) viewModel.loadMoreMembers()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(state.community?.name ?: "Społeczność") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
				actions = {
					// Zarządzanie widoczne tylko dla właściciela.
					if (state.community?.isOwner == true) {
						TextButton(onClick = viewModel::openEditDialog) { Text("Edytuj") }
						TextButton(
							onClick = viewModel::openDeleteDialog,
							colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
						) { Text("Usuń") }
					}
				},
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading ->
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

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

				state.community != null -> LazyColumn(
					state = listState,
					contentPadding = PaddingValues(16.dp),
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					item(key = "header") {
						CommunityHeader(
							community = state.community,
							isBusy = state.isBusy,
							statusMessage = state.statusMessage,
							onToggleMembership = viewModel::toggleMembership,
						)
					}
					item(key = "tabs") {
						TabRow(selectedTabIndex = state.selectedTab.ordinal) {
							Tab(
								selected = state.selectedTab == CommunityTab.POSTS,
								onClick = { viewModel.selectTab(CommunityTab.POSTS) },
								text = { Text("Posty") },
							)
							Tab(
								selected = state.selectedTab == CommunityTab.MEMBERS,
								onClick = { viewModel.selectTab(CommunityTab.MEMBERS) },
								text = { Text("Członkowie (${state.community.memberCount})") },
							)
						}
					}
					if (state.selectedTab == CommunityTab.POSTS) {
						val ban = state.membership?.ban
						if (state.membership?.isBanned == true && ban != null) {
							item(key = "ban") { BanBanner(ban) }
						}
						if (state.canWrite) {
							item(key = "composer") {
								PostComposer(
									draft = state.draft,
									isPublishing = state.isPublishing,
									canPublish = state.canPublish,
									errorMessage = state.publishError,
									onDraftChange = viewModel::onDraftChange,
									onPublish = { viewModel.publish(onPublished = postsViewModel::prepend) },
								)
							}
						}
						postListItems(
							viewModel = postsViewModel,
							emptyText = "W tej społeczności nie ma jeszcze postów.",
							onOpenComments = onOpenComments,
							onOpenAuthor = onOpenUserProfile,
						)
					}
					if (state.selectedTab == CommunityTab.MEMBERS) {
						items(state.members, key = { it.userId }) { member ->
							MemberRow(member, onOpenProfile = { onOpenUserProfile(member.userId) })
						}
					}
					if (state.selectedTab == CommunityTab.MEMBERS && state.isLoadingMoreMembers) {
						item(key = "members-footer") {
							Box(
								modifier = Modifier.fillMaxWidth().padding(16.dp),
								contentAlignment = Alignment.Center,
							) {
								CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun CommunityHeader(
	community: Community,
	isBusy: Boolean,
	statusMessage: String?,
	onToggleMembership: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		if (!community.description.isNullOrBlank()) {
			Text(text = community.description!!, style = MaterialTheme.typography.bodyMedium)
		}
		if (statusMessage != null) {
			Text(
				text = statusMessage,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
			)
		}
		when {
			community.isOwner -> Text(
				text = "Jesteś właścicielem tej społeczności.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.primary,
			)

			community.isMember -> OutlinedButton(onClick = onToggleMembership, enabled = !isBusy) {
				Text(if (isBusy) "…" else "Opuść społeczność")
			}

			else -> Button(onClick = onToggleMembership, enabled = !isBusy) {
				Text(if (isBusy) "…" else "Dołącz")
			}
		}
		HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
	}
}

/**
 * Mój ban z powodem i terminem (backend ADR 0015). Społeczność czytać wolno dalej
 * — ban odcina tylko pisanie — więc baner stoi nad postami, a nie zamiast nich.
 */
@Composable
private fun BanBanner(ban: BanDetails) {
	Card(
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(
				text = if (ban.expiresAt == null) {
					"Masz bezterminowy zakaz publikowania w tej społeczności."
				} else {
					"Masz zakaz publikowania w tej społeczności do ${formatDateTime(ban.expiresAt!!)}."
				},
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onErrorContainer,
			)
			if (!ban.reason.isNullOrBlank()) {
				Text(
					text = "Powód: ${ban.reason}",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onErrorContainer,
				)
			}
		}
	}
}

@Composable
private fun PostComposer(
	draft: String,
	isPublishing: Boolean,
	canPublish: Boolean,
	errorMessage: String?,
	onDraftChange: (String) -> Unit,
	onPublish: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		OutlinedTextField(
			value = draft,
			onValueChange = onDraftChange,
			label = { Text("Napisz post w społeczności") },
			enabled = !isPublishing,
			minLines = 2,
			isError = draft.length > MAX_POST_LENGTH,
			supportingText = { Text("${draft.length}/$MAX_POST_LENGTH") },
			modifier = Modifier.fillMaxWidth(),
		)
		if (errorMessage != null) {
			Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
		}
		Button(onClick = onPublish, enabled = canPublish, modifier = Modifier.align(Alignment.End)) {
			Text(if (isPublishing) "Publikowanie…" else "Opublikuj")
		}
	}
}

private fun formatDateTime(iso: String): String = runCatching {
	DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")
		.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

@Composable
private fun MemberRow(member: CommunityMember, onOpenProfile: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		// Avatar + nazwa prowadzą do profilu członka; etykieta „Właściciel" zostaje osobno.
		Row(
			modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Avatar(displayName = member.displayName, avatarUrl = member.avatarUrl, size = 36.dp)
			Text(
				text = member.displayName,
				style = MaterialTheme.typography.bodyLarge,
			)
		}
		if (member.isOwner) {
			Text(
				text = "Właściciel",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary,
			)
		}
	}
}

@Composable
private fun EditCommunityDialog(
	name: String,
	description: String,
	isSaving: Boolean,
	canSave: Boolean,
	errorMessage: String?,
	onNameChange: (String) -> Unit,
	onDescriptionChange: (String) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Edytuj społeczność") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = onNameChange,
					label = { Text("Nazwa") },
					singleLine = true,
					enabled = !isSaving,
					isError = errorMessage != null,
					modifier = Modifier.fillMaxWidth(),
				)
				OutlinedTextField(
					value = description,
					onValueChange = onDescriptionChange,
					label = { Text("Opis (opcjonalny)") },
					enabled = !isSaving,
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
			TextButton(onClick = onConfirm, enabled = canSave) {
				Text(if (isSaving) "Zapisywanie…" else "Zapisz")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Anuluj") }
		},
	)
}

@Composable
private fun DeleteCommunityDialog(
	isDeleting: Boolean,
	errorMessage: String?,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Usunąć społeczność?") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					"Ta operacja jest nieodwracalna — społeczność i członkostwa zostaną trwale usunięte.",
					style = MaterialTheme.typography.bodyMedium,
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
			TextButton(
				onClick = onConfirm,
				enabled = !isDeleting,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) {
				Text(if (isDeleting) "Usuwanie…" else "Usuń")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Anuluj") }
		},
	)
}
