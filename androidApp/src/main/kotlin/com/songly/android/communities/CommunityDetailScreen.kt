package com.songly.android.communities

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.songly.shared.model.CommunityMember

/**
 * Ekran szczegółów społeczności (Compose, natywny UI — §14): nagłówek z opisem i
 * akcją dołącz/opuść oraz lista członków (paginacja). Stan z [CommunityDetailViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
	viewModel: CommunityDetailViewModel,
	onBack: () -> Unit,
) {
	val state = viewModel.uiState
	val listState = rememberLazyListState()

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	val shouldLoadMore by remember {
		derivedStateOf {
			val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			val total = listState.layoutInfo.totalItemsCount
			total > 0 && lastVisible >= total - 3
		}
	}
	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore) viewModel.loadMoreMembers()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(state.community?.name ?: "Społeczność") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
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
					item(key = "members-header") {
						Text(
							text = "Członkowie (${state.community.memberCount})",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.SemiBold,
							modifier = Modifier.padding(top = 8.dp),
						)
					}
					items(state.members, key = { it.userId }) { member ->
						MemberRow(member)
					}
					if (state.isLoadingMoreMembers) {
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

@Composable
private fun MemberRow(member: CommunityMember) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = member.displayName,
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.weight(1f),
		)
		if (member.isOwner) {
			Text(
				text = "Właściciel",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary,
			)
		}
	}
}
