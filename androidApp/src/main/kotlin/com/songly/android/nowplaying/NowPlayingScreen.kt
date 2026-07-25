package com.songly.android.nowplaying

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songly.shared.model.NowPlayingStatus

/**
 * Ekran „teraz słucham" (Compose, natywny UI — §14): aktualny status, integracja
 * ze Spotify (OAuth przez przeglądarkę) i uniwersalny ręczny status. Stan z
 * [NowPlayingViewModel]; dane z modułu now-playing przez `shared`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
	viewModel: NowPlayingViewModel,
	onBack: () -> Unit,
	onOpenFriendsPresence: () -> Unit,
) {
	val state = viewModel.uiState
	val context = LocalContext.current

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Teraz gram") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
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

					CurrentStatusCard(status = state.status)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = viewModel::load, enabled = !state.isBusy) { Text("Odśwież") }
						OutlinedButton(onClick = onOpenFriendsPresence) { Text("Znajomi teraz") }
					}

					HorizontalDivider()

					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Pokazuj status znajomym",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.SemiBold,
							)
							Text(
								text = "Wyłączenie chowa Twój status wszędzie: na liście znajomych i w ich " +
									"strumieniu na żywo.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = state.shareWithFriends,
							onCheckedChange = viewModel::onShareWithFriendsChange,
							enabled = !state.isBusy,
						)
					}

					HorizontalDivider()

					Text("Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
					Text(
						text = "Połącz konto, żeby automatycznie pokazywać, czego słuchasz na Spotify.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Button(
							onClick = {
								viewModel.connectSpotify { url ->
									context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
								}
							},
							enabled = !state.isBusy,
						) {
							Text("Połącz ze Spotify")
						}
						OutlinedButton(onClick = viewModel::disconnectSpotify, enabled = !state.isBusy) {
							Text("Rozłącz")
						}
					}

					HorizontalDivider()

					Text(
						text = "Ręczny status",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.SemiBold,
					)
					Text(
						text = "Uniwersalny fallback dla serwisów bez integracji (Apple Music, YouTube Music, Tidal…).",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					OutlinedTextField(
						value = state.title,
						onValueChange = viewModel::onTitleChange,
						label = { Text("Tytuł utworu") },
						singleLine = true,
						enabled = !state.isBusy,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = state.artist,
						onValueChange = viewModel::onArtistChange,
						label = { Text("Wykonawca") },
						singleLine = true,
						enabled = !state.isBusy,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = state.album,
						onValueChange = viewModel::onAlbumChange,
						label = { Text("Album") },
						singleLine = true,
						enabled = !state.isBusy,
						modifier = Modifier.fillMaxWidth(),
					)
					OutlinedTextField(
						value = state.source,
						onValueChange = viewModel::onSourceChange,
						label = { Text("Serwis (np. YouTube Music)") },
						singleLine = true,
						enabled = !state.isBusy,
						modifier = Modifier.fillMaxWidth(),
					)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
						OutlinedButton(onClick = viewModel::clearManual, enabled = !state.isBusy) { Text("Wyczyść") }
						Button(onClick = viewModel::setManual, enabled = state.canSetManual) { Text("Ustaw") }
					}
				}
			}
		}
	}
}

@Composable
private fun CurrentStatusCard(status: NowPlayingStatus?) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.padding(16.dp)) {
			if (status == null) {
				Text("Nic teraz nie gra.", style = MaterialTheme.typography.bodyLarge)
			} else {
				Text(
					text = status.title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
				val artist = status.artist
				if (!artist.isNullOrBlank()) {
					Text(text = artist, style = MaterialTheme.typography.bodyMedium)
				}
				val meta = buildString {
					append(if (status.playing) "▶ odtwarzane" else "❚❚ wstrzymane")
					append("  •  ")
					append(status.provider)
				}
				Text(
					text = meta,
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 6.dp),
				)
			}
		}
	}
}
