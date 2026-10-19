package com.songly.android.matches

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.songly.android.ui.BackButton
import com.songly.shared.model.Match

/**
 * Ekran dopasowań (Compose, natywny UI — §14): ranking osób o podobnym guście
 * muzycznym (score Jaccarda jako procent) z etykietami wspólnych gatunków i
 * zaproszeniem do znajomych. Stan z [MatchesViewModel]; dane z modułu matching.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MatchesScreen(
	viewModel: MatchesViewModel,
	onBack: () -> Unit,
	onOpenUserProfile: (String) -> Unit,
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

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Dopasowani") },
				navigationIcon = { BackButton(onBack) },
			)
		},
	) { padding ->
		PullToRefreshBox(
			isRefreshing = state.isRefreshing,
			onRefresh = viewModel::pullToRefresh,
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
			when {
				state.isLoading && state.matches.isEmpty() ->
					CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> CenteredMessage {
					Text(state.errorMessage)
					Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				state.isEmpty -> CenteredMessage {
					Text(
						// Pusto bywa też przy wybranych gatunkach: nikt inny ich nie wskazał albo
						// wskazali je tylko znajomi, których backend z dopasowań pomija. Nie
						// twierdzimy więc, że użytkownikowi czegoś brakuje.
						"Brak nowych osób o podobnym guście. Znajomych tu nie pokazujemy. Sprawdź, czy masz wybrane ulubione gatunki w profilu, albo zajrzyj później.",
						style = MaterialTheme.typography.bodyMedium,
					)
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
					items(state.matches, key = { it.userId }) { match ->
						MatchCard(
							match = match,
							genreNames = viewModel.sharedGenreNames(match),
							invited = match.userId in state.invitedIds,
							enabled = !state.isBusy,
							onInvite = { viewModel.invite(match.userId) },
							onOpenProfile = { onOpenUserProfile(match.userId) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(
	match: Match,
	genreNames: List<String>,
	invited: Boolean,
	enabled: Boolean,
	onInvite: () -> Unit,
	onOpenProfile: () -> Unit,
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				// Avatar + nazwa prowadzą do profilu; wskaźnik dopasowania zostaje osobno.
				Row(
					modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Avatar(displayName = match.displayName, avatarUrl = match.avatarUrl, size = 40.dp)
					Text(
						text = match.displayName,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
					)
				}
				Text(
					text = "${match.scorePercent}% dopasowania",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.primary,
				)
			}
			if (genreNames.isNotEmpty()) {
				FlowRow(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.padding(top = 8.dp),
				) {
					genreNames.forEach { name ->
						AssistChip(onClick = {}, label = { Text(name) })
					}
				}
			}
			if (invited) {
				Text(
					text = "Zaproszenie wysłane",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 10.dp).align(Alignment.End),
				)
			} else {
				Button(
					onClick = onInvite,
					enabled = enabled,
					modifier = Modifier.padding(top = 10.dp).align(Alignment.End),
				) {
					Text("Zaproś do znajomych")
				}
			}
		}
	}
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
