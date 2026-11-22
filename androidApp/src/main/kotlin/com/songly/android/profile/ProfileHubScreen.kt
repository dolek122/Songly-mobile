package com.songly.android.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songly.android.ui.Avatar
import com.songly.android.ui.NavigationRow
import com.songly.android.ui.TabTopBar

@Composable
fun ProfileHubScreen(
	viewModel: ProfileViewModel,
	isAdmin: Boolean,
	onEditProfile: () -> Unit,
	onOpenMailPreferences: () -> Unit,
	onOpenAdmin: () -> Unit,
	onLogout: () -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		containerColor = Color.Transparent,
		topBar = { TabTopBar(title = "Profil") },
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			ProfileHeader(state = state, onRetry = viewModel::load)

			if (state.hasDefaultName) {
				DefaultNameHint(onSetName = onEditProfile)
			}

			OutlinedButton(
				onClick = onEditProfile,
				modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
			) {
				Text("Edytuj profil")
			}

			HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

			NavigationRow(
				icon = Icons.Outlined.Email,
				title = "Powiadomienia e-mail",
				subtitle = "Które sprawy mają przychodzić mailem",
				onClick = onOpenMailPreferences,
			)
			if (isAdmin) {
				NavigationRow(
					icon = Icons.Outlined.AdminPanelSettings,
					title = "Panel administratora",
					subtitle = "Zawieszenia kont i eskalacje banów",
					onClick = onOpenAdmin,
				)
			}

			HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

			NavigationRow(
				icon = Icons.AutoMirrored.Filled.Logout,
				title = "Wyloguj się",
				onClick = onLogout,
				showChevron = false,
			)
		}
	}
}

@Composable
private fun DefaultNameHint(onSetName: () -> Unit) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				text = "Tę nazwę nadaliśmy automatycznie. Ustaw własną, żeby znajomi mogli Cię znaleźć.",
				style = MaterialTheme.typography.bodyMedium,
			)
			TextButton(onClick = onSetName, modifier = Modifier.align(Alignment.End)) {
				Text("Ustaw nazwę")
			}
		}
	}
}

@Composable
private fun ProfileHeader(state: ProfileUiState, onRetry: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Avatar(displayName = state.displayName, avatarUrl = state.avatarUrl, size = 88.dp)
		Column(modifier = Modifier.weight(1f)) {
			when {
				state.displayName.isNotBlank() -> {
					Text(
						text = state.displayName,
						style = MaterialTheme.typography.headlineMedium,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
					if (state.bio.isNotBlank()) {
						Text(
							text = state.bio,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 3,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(top = 4.dp),
						)
					}
				}

				state.isLoading -> Text(
					text = "Wczytywanie…",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				else -> {
					Text(
						text = state.errorMessage ?: "Nie udało się wczytać profilu.",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
				}
			}
		}
	}
}
