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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.shared.model.Community
import com.songly.shared.model.BanDetails
import com.songly.shared.model.CommunityMember
import com.songly.android.post.MAX_POST_LENGTH
import com.songly.android.moderation.CommunityModerationViewModel
import com.songly.android.moderation.ReportPostDialog
import com.songly.android.moderation.ReportPostViewModel
import com.songly.android.moderation.appealLabel
import com.songly.android.moderation.moderationItems
import com.songly.android.post.PostListDialogs
import com.songly.android.post.PostListViewModel
import com.songly.android.post.postListItems
import com.songly.android.ui.formatDateTime

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
	moderationViewModel: CommunityModerationViewModel? = null,
	createReportViewModel: ((postId: String) -> ReportPostViewModel)? = null,
	onOpenPost: (postId: String) -> Unit = {},
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()
	var reportingPostId by remember { mutableStateOf<String?>(null) }
	var reportStatus by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(Unit) {
		viewModel.load()
		postsViewModel.refresh()
	}

	PostListDialogs(postsViewModel)

	val reportingId = reportingPostId
	if (reportingId != null && createReportViewModel != null) {
		val reportViewModel = remember(reportingId) { createReportViewModel(reportingId) }
		ReportPostDialog(
			viewModel = reportViewModel,
			onReported = {
				reportingPostId = null
				reportStatus = "Dziękujemy — zgłoszenie trafiło do moderatorów."
			},
			onDismiss = { reportingPostId = null },
		)
	}

	val banTarget = state.banTarget
	if (banTarget != null) {
		BanMemberDialog(
			member = banTarget,
			state = state,
			onReasonChange = viewModel::onBanReasonChange,
			onDaysChange = viewModel::onBanDaysChange,
			onConfirm = { viewModel.confirmBan(onBanned = { moderationViewModel?.invalidateBans() }) },
			onDismiss = viewModel::dismissBanDialog,
		)
	}

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
							if (state.canModerate && moderationViewModel != null) {
								Tab(
									selected = state.selectedTab == CommunityTab.MODERATION,
									onClick = {
										viewModel.selectTab(CommunityTab.MODERATION)
										moderationViewModel.select(moderationViewModel.uiState.section)
									},
									text = { Text("Moderacja") },
								)
							}
						}
					}
					if (state.selectedTab == CommunityTab.POSTS) {
						val ban = state.membership?.ban
						if (state.membership?.isBanned == true && ban != null) {
							item(key = "ban") {
								BanBanner(
									ban = ban,
									appealText = state.appealText,
									isAppealing = state.isAppealing,
									canAppeal = state.canAppeal,
									appealError = state.appealError,
									onAppealTextChange = viewModel::onAppealTextChange,
									onSubmitAppeal = viewModel::submitAppeal,
									onEscalate = viewModel::escalateAppeal,
								)
							}
						}
						if (reportStatus != null) {
							item(key = "report-status") {
								Text(reportStatus!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
							}
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
							canModerate = state.canModerate,
							onReport = if (createReportViewModel != null) {
								{ reportingPostId = it; reportStatus = null }
							} else {
								null
							},
						)
					}
					if (state.selectedTab == CommunityTab.MEMBERS) {
						items(state.members, key = { it.userId }) { member ->
							MemberRow(
								member = member,
								// Właściciela nie da się moderować; siebie samego też nie banujemy.
								canBan = state.canModerate && !member.isOwner && member.userId != postsViewModel.uiState.currentUserId,
								canManageRole = state.community.isOwner && !member.isOwner,
								roleBusy = state.roleChangeUserId == member.userId,
								onOpenProfile = { onOpenUserProfile(member.userId) },
								onBan = { viewModel.openBanDialog(member) },
								onToggleModerator = { viewModel.toggleModerator(member) },
							)
						}
					}
					if (state.selectedTab == CommunityTab.MODERATION && moderationViewModel != null) {
						val names = state.members.associate { it.userId to it.displayName }
						moderationItems(
							viewModel = moderationViewModel,
							displayNameOf = { id -> id?.let { names[it] } ?: "użytkownik spoza listy członków" },
							onOpenPost = onOpenPost,
							onOpenUserProfile = onOpenUserProfile,
						)
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
private fun BanBanner(
	ban: BanDetails,
	appealText: String,
	isAppealing: Boolean,
	canAppeal: Boolean,
	appealError: String?,
	onAppealTextChange: (String) -> Unit,
	onSubmitAppeal: () -> Unit,
	onEscalate: () -> Unit,
) {
	Card(
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

			// Jedno odwołanie na ban; po odrzuceniu jedna eskalacja do administracji
			// (ADR 0017, 0028). Każdy stan ma tu dokładnie jedno możliwe działanie.
			when (ban.appealStatus) {
				null -> {
					OutlinedTextField(
						value = appealText,
						onValueChange = onAppealTextChange,
						label = { Text("Odwołanie do moderacji") },
						enabled = !isAppealing,
						minLines = 2,
						isError = appealText.length > 1000,
						supportingText = { Text("${appealText.length}/1000 · można się odwołać tylko raz") },
						modifier = Modifier.fillMaxWidth(),
					)
					Button(onClick = onSubmitAppeal, enabled = canAppeal, modifier = Modifier.align(Alignment.End)) {
						Text(if (isAppealing) "Wysyłanie…" else "Odwołaj się")
					}
				}

				"REJECTED" -> {
					AppealStatus(appealLabel(ban.appealStatus))
					OutlinedButton(onClick = onEscalate, enabled = !isAppealing, modifier = Modifier.align(Alignment.End)) {
						Text(if (isAppealing) "Wysyłanie…" else "Przekaż administracji")
					}
				}

				else -> AppealStatus(appealLabel(ban.appealStatus))
			}
			if (appealError != null) {
				Text(appealError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
			}
		}
	}
}

@Composable
private fun AppealStatus(label: String?) {
	if (label != null) {
		Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
	}
}

@Composable
private fun BanMemberDialog(
	member: CommunityMember,
	state: CommunityDetailUiState,
	onReasonChange: (String) -> Unit,
	onDaysChange: (Int?) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Zbanować: ${member.displayName}?") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					"Ban odbiera członkostwo i możliwość pisania. Powód zobaczy zbanowana osoba.",
					style = MaterialTheme.typography.bodySmall,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					BAN_DURATIONS.forEach { days ->
						FilterChip(
							selected = state.banDays == days,
							onClick = { onDaysChange(days) },
							label = { Text(if (days == null) "Na zawsze" else "$days d") },
							enabled = !state.isBanning,
						)
					}
				}
				OutlinedTextField(
					value = state.banReason,
					onValueChange = onReasonChange,
					label = { Text("Powód (opcjonalnie)") },
					enabled = !state.isBanning,
					isError = state.banReason.length > 500,
					minLines = 2,
					modifier = Modifier.fillMaxWidth(),
				)
				if (state.banError != null) {
					Text(state.banError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = onConfirm,
				enabled = state.canBan,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) {
				Text(if (state.isBanning) "Banowanie…" else "Zbanuj")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !state.isBanning) { Text("Anuluj") }
		},
	)
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

@Composable
private fun MemberRow(
	member: CommunityMember,
	canBan: Boolean,
	canManageRole: Boolean,
	roleBusy: Boolean,
	onOpenProfile: () -> Unit,
	onBan: () -> Unit,
	onToggleModerator: () -> Unit,
) {
	Column {
		MemberIdentity(member, onOpenProfile)
		if (canBan || canManageRole) {
			Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.End)) {
				if (canManageRole) {
					TextButton(onClick = onToggleModerator, enabled = !roleBusy) {
						Text(if (member.isModerator) "Odbierz moderatora" else "Nadaj moderatora")
					}
				}
				if (canBan) {
					TextButton(
						onClick = onBan,
						colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
					) { Text("Zbanuj") }
				}
			}
		}
	}
}

@Composable
private fun MemberIdentity(member: CommunityMember, onOpenProfile: () -> Unit) {
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
		val roleLabel = when {
			member.isOwner -> "Właściciel"
			member.isModerator -> "Moderator"
			else -> null
		}
		if (roleLabel != null) {
			Text(
				text = roleLabel,
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
