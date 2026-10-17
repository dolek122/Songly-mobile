package com.songly.android.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.songly.android.media.ImagePreparer
import com.songly.android.ui.Avatar

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
	onPasswordChanged: () -> Unit = {},
) {
	val state = viewModel.uiState
	val context = LocalContext.current

	// Systemowy wybór zdjęć — bez uprawnień do całej galerii; aplikacja dostaje
	// dostęp tylko do jednego wskazanego pliku.
	val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri != null) viewModel.changeAvatar { ImagePreparer.prepare(context, uri) }
	}

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	// Po usunięciu konta sesja jest już wyczyszczona — wróć do ekranu logowania.
	LaunchedEffect(state.accountDeleted) {
		if (state.accountDeleted) onAccountDeleted()
	}

	// Zmiana hasła zakończyła wszystkie sesje, łącznie z tą — tak samo do logowania.
	LaunchedEffect(state.passwordChanged) {
		if (state.passwordChanged) onPasswordChanged()
	}

	if (state.showPasswordDialog) {
		ChangePasswordDialog(state, viewModel)
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
					Avatar(
						displayName = state.displayName,
						avatarUrl = state.avatarUrl,
						size = 88.dp,
						modifier = Modifier.align(Alignment.CenterHorizontally),
					)
					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier.align(Alignment.CenterHorizontally),
					) {
						TextButton(
							onClick = {
								pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
							},
							enabled = !state.isUpdatingAvatar,
						) {
							Text(if (state.isUpdatingAvatar) "Wysyłanie…" else "Zmień zdjęcie")
						}
						if (state.avatarUrl != null) {
							TextButton(onClick = viewModel::removeAvatar, enabled = !state.isUpdatingAvatar) {
								Text("Usuń zdjęcie")
							}
						}
					}
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

					Text("Bezpieczeństwo", style = MaterialTheme.typography.titleMedium)
					Text(
						"Zmiana hasła wyloguje Cię ze wszystkich urządzeń, także z tego.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					OutlinedButton(onClick = viewModel::openPasswordDialog, modifier = Modifier.align(Alignment.End)) {
						Text("Zmień hasło")
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

@Composable
private fun ChangePasswordDialog(state: ProfileUiState, viewModel: ProfileViewModel) {
	AlertDialog(
		onDismissRequest = viewModel::dismissPasswordDialog,
		title = { Text("Zmień hasło") },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				PasswordField("Obecne hasło", state.currentPassword, viewModel::onCurrentPasswordChange, !state.isChangingPassword)
				PasswordField(
					label = "Nowe hasło",
					value = state.newPassword,
					onValueChange = viewModel::onNewPasswordChange,
					enabled = !state.isChangingPassword,
					error = if (state.newPasswordInvalid) "Hasło musi mieć od 8 do 72 znaków" else null,
				)
				PasswordField(
					label = "Powtórz nowe hasło",
					value = state.confirmNewPassword,
					onValueChange = viewModel::onConfirmNewPasswordChange,
					enabled = !state.isChangingPassword,
					error = if (state.newPasswordsMismatch) "Hasła nie są takie same" else null,
				)
				if (state.passwordError != null) {
					Text(state.passwordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = viewModel::changePassword, enabled = state.canChangePassword) {
				Text(if (state.isChangingPassword) "Zmienianie…" else "Zmień hasło")
			}
		},
		dismissButton = {
			TextButton(onClick = viewModel::dismissPasswordDialog, enabled = !state.isChangingPassword) { Text("Anuluj") }
		},
	)
}

@Composable
private fun PasswordField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	enabled: Boolean,
	error: String? = null,
) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		label = { Text(label) },
		singleLine = true,
		enabled = enabled,
		isError = error != null,
		supportingText = error?.let { { Text(it) } },
		visualTransformation = PasswordVisualTransformation(),
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
		modifier = Modifier.fillMaxWidth(),
	)
}
