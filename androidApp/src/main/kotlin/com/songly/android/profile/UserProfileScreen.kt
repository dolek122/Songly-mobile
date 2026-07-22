package com.songly.android.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.songly.android.ui.Avatar
import com.songly.shared.model.PublicProfile

/**
 * Ekran cudzego profilu (read-only, §14 natywny UI): avatar, nazwa, bio i ulubione
 * gatunki innego użytkownika. Dane z [UserProfileViewModel]; akcje relacji
 * (zaproszenie/blokada) żyją na razie w ekranie znajomych i dopasowań.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(
	viewModel: UserProfileViewModel,
	onBack: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
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

				state.profile != null -> ProfileContent(state.profile)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(profile: PublicProfile) {
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
				text = profile.bio,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
			)
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
