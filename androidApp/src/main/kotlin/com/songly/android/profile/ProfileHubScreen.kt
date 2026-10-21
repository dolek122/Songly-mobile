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

/**
 * Zakładka „Profil": kim jestem i wszystko, co dotyczy mojego konta, w jednym
 * miejscu — edycja profilu, powiadomienia e-mail, panel administratora (tylko dla
 * administracji) i wylogowanie. Wcześniej te rzeczy były rozsiane po menu „⋮" i
 * długim formularzu, więc trzeba było ich szukać.
 *
 * Wiersze są widoczne zawsze, także gdy profil się nie wczytał — wylogowanie nie
 * może zależeć od tego, czy serwer akurat odpowiada.
 */
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

			// Zdjęcie, nazwa, bio, ulubione gatunki i hasło — wszystko na jednym ekranie edycji.
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

/**
 * Duży awatar z nazwą i bio — jak nagłówek profilu w aplikacjach muzycznych.
 * Przy błędzie wczytywania zostaje sam komunikat z ponowieniem, bez pustych pól.
 */
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
