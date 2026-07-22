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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
	onAccountDeleted: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	// Po usunięciu konta sesja jest już wyczyszczona — wróć do ekranu logowania.
	LaunchedEffect(state.accountDeleted) {
		if (state.accountDeleted) onAccountDeleted()
	}

	if (state.showDeleteDialog) {
		DeleteAccountDialog(
			password = state.deletePassword,
			isDeleting = state.isDeleting,
			canConfirm = state.canConfirmDelete,
			errorMessage = state.deleteError,
			onPasswordChange = viewModel::onDeletePasswordChange,
			onConfirm = viewModel::confirmDelete,
			onDismiss = viewModel::dismissDeleteDialog,
		)
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

					HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

					Text(
						"Strefa niebezpieczna",
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.error,
					)
					Text(
						"Usunięcie konta jest nieodwracalne — trwale kasuje Twoje dane " +
							"(profil, posty, znajomych i pozostałe).",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					OutlinedButton(
						onClick = viewModel::openDeleteDialog,
						colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
						modifier = Modifier.align(Alignment.End),
					) {
						Text("Usuń konto")
					}
				}
			}
		}
	}
}

/** Dialog potwierdzenia usunięcia konta — wymusza ponowne podanie hasła (§9). */
@Composable
private fun DeleteAccountDialog(
	password: String,
	isDeleting: Boolean,
	canConfirm: Boolean,
	errorMessage: String?,
	onPasswordChange: (String) -> Unit,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Usunąć konto?") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					"Ta operacja jest nieodwracalna. Potwierdź hasłem, aby trwale usunąć konto.",
					style = MaterialTheme.typography.bodyMedium,
				)
				OutlinedTextField(
					value = password,
					onValueChange = onPasswordChange,
					label = { Text("Hasło") },
					singleLine = true,
					enabled = !isDeleting,
					isError = errorMessage != null,
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(
						keyboardType = KeyboardType.Password,
						imeAction = ImeAction.Done,
					),
					keyboardActions = KeyboardActions(onDone = { onConfirm() }),
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
			TextButton(
				onClick = onConfirm,
				enabled = canConfirm,
				colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) {
				Text(if (isDeleting) "Usuwanie…" else "Usuń konto")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Anuluj") }
		},
	)
}
