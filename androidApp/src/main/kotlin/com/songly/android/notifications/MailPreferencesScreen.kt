package com.songly.android.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.android.ui.BackButton

/**
 * Ekran preferencji poczty (ADR 0020), otwierany z powiadomień. Stan z
 * [MailPreferencesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailPreferencesScreen(viewModel: MailPreferencesViewModel, onBack: () -> Unit) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Powiadomienia e-mail") },
				navigationIcon = { BackButton(onBack) },
			)
		},
	) { padding ->
		val preferences = state.preferences
		when {
			state.isLoading && preferences == null -> Box(
				modifier = Modifier.fillMaxSize().padding(padding),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator()
			}

			preferences == null -> Column(
				modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text(state.errorMessage ?: "Nie udało się wczytać ustawień.")
				Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
					Text("Spróbuj ponownie")
				}
			}

			else -> Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(padding)
					.verticalScroll(rememberScrollState())
					.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				// Skrzynki w aplikacji nie da się tu wyłączyć (ADR 0020) — mówimy to
				// wprost, zanim ktoś uzna przełączniki za wyciszanie powiadomień.
				Text(
					text = "Te ustawienia dotyczą tylko poczty. W aplikacji powiadomienia o decyzjach " +
						"moderacji pojawiają się zawsze.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				HorizontalDivider()

				PreferenceRow(
					title = "Decyzje dotyczące mnie",
					description = "Ban w społeczności, usunięcie Twojego posta, zawieszenie konta oraz " +
						"rozstrzygnięcia Twoich zgłoszeń i odwołań.",
					checked = preferences.decisionsAboutMe,
					enabled = !state.isSaving,
					onCheckedChange = viewModel::onDecisionsAboutMeChange,
				)

				HorizontalDivider()

				PreferenceRow(
					title = "Sprawy do rozpatrzenia",
					description = "Nowe zgłoszenia i odwołania w społecznościach, które moderujesz, a dla " +
						"administracji także eskalacje i odwołania od zawieszeń.",
					checked = preferences.moderationQueue,
					enabled = !state.isSaving,
					onCheckedChange = viewModel::onModerationQueueChange,
				)

				state.statusMessage?.let {
					Text(it, style = MaterialTheme.typography.bodySmall)
				}
			}
		}
	}
}

@Composable
private fun PreferenceRow(
	title: String,
	description: String,
	checked: Boolean,
	enabled: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	// Przełącznikiem jest cały wiersz, nie sam Switch: TalkBack czyta wtedy etykietę
	// razem ze stanem (inaczej słyszy się „przełącznik, włączony" bez słowa o tym,
	// czego dotyczy), a cel dotyku obejmuje też tekst.
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
			Text(
				text = description,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Switch(checked = checked, onCheckedChange = null, enabled = enabled)
	}
}
