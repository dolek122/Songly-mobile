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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ekran profilu (Compose, natywny UI — §14): edycja danych profilowych i wybór
 * ulubionych gatunków. Stan z [ProfileViewModel]; dane z modułu `profile`
 * backendu przez `shared`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
	viewModel: ProfileViewModel,
	onBack: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Profil") },
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
					Text(state.errorMessage)
					Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
						Text("Spróbuj ponownie")
					}
				}

				else -> Column(
					modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					if (state.statusMessage != null) {
						Text(
							text = state.statusMessage,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.primary,
						)
					}

					Text("Twoje dane", style = MaterialTheme.typography.titleMedium)
					OutlinedTextField(
						value = state.displayName,
						onValueChange = viewModel::onDisplayNameChange,
						label = { Text("Nazwa wyświetlana") },
						singleLine = true,
						enabled = !state.isSavingProfile,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = state.bio,
						onValueChange = viewModel::onBioChange,
						label = { Text("Bio") },
						enabled = !state.isSavingProfile,
						minLines = 3,
						modifier = Modifier.fillMaxWidth(),
					)
					Button(
						onClick = viewModel::saveProfile,
						enabled = state.canSaveProfile,
						modifier = Modifier.align(Alignment.End),
					) {
						Text(if (state.isSavingProfile) "Zapisywanie…" else "Zapisz profil")
					}

					HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

					Text("Ulubione gatunki", style = MaterialTheme.typography.titleMedium)
					if (state.allGenres.isEmpty()) {
						Text("Brak dostępnych gatunków.", style = MaterialTheme.typography.bodyMedium)
					} else {
						FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							state.allGenres.forEach { genre ->
								FilterChip(
									selected = genre.id in state.selectedGenreIds,
									onClick = { viewModel.toggleGenre(genre.id) },
									label = { Text(genre.name) },
									enabled = !state.isSavingGenres,
								)
							}
						}
					}
					Button(
						onClick = viewModel::saveGenres,
						enabled = state.canSaveGenres,
						modifier = Modifier.align(Alignment.End),
					) {
						Text(if (state.isSavingGenres) "Zapisywanie…" else "Zapisz gatunki")
					}
				}
			}
		}
	}
}
