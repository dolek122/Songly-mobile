package com.songly.android.nowplaying

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.NavigationRow
import com.songly.android.ui.SectionTitle
import com.songly.shared.model.NowPlayingStatus

/**
 * Zakładka „Muzyka" (Compose, natywny UI — §14): co teraz gram, czego słuchają
 * znajomi, integracja ze Spotify (OAuth przez przeglądarkę) i uniwersalny ręczny
 * status. Stan z [NowPlayingViewModel]; dane z modułu now-playing przez `shared`.
 *
 * [onBack] jest `null`, gdy ekran jest zakładką dolnego paska — wtedy nie ma
 * dokąd wracać, więc w pasku nie ma strzałki.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
	viewModel: NowPlayingViewModel,
	onOpenFriendsPresence: () -> Unit,
	onBack: (() -> Unit)? = null,
) {
	val state = viewModel.uiState
	val context = LocalContext.current

	LaunchedEffect(Unit) {
		viewModel.load()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Muzyka") },
				navigationIcon = { if (onBack != null) BackButton(onBack) },
			)
		},
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding)) {
			when {
				state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

				state.errorMessage != null -> EmptyState(icon = Icons.Outlined.CloudOff, message = state.errorMessage) {
					Button(onClick = viewModel::load) { Text("Spróbuj ponownie") }
				}

				else -> Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(rememberScrollState())
						.padding(horizontal = 16.dp, vertical = 8.dp),
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
					OutlinedButton(onClick = viewModel::load, enabled = !state.isBusy) { Text("Odśwież") }

					NavigationRow(
						icon = Icons.Outlined.Headphones,
						title = "Znajomi teraz",
						subtitle = "Zobacz na żywo, czego słuchają Twoi znajomi",
						onClick = onOpenFriendsPresence,
					)

					HorizontalDivider()

					// Cały wiersz jest przełącznikiem — TalkBack czyta etykietę razem ze stanem.
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.toggleable(
								value = state.shareWithFriends,
								enabled = !state.isBusy,
								role = Role.Switch,
								onValueChange = viewModel::onShareWithFriendsChange,
							)
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(16.dp),
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(text = "Pokazuj status znajomym", style = MaterialTheme.typography.titleMedium)
							Text(
								text = "Wyłączenie chowa Twój status wszędzie: na liście znajomych i w ich " +
									"strumieniu na żywo.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Switch(
							checked = state.shareWithFriends,
							onCheckedChange = null,
							enabled = !state.isBusy,
						)
					}

					SectionTitle("Spotify")
					Text(
						text = "Połącz konto, żeby automatycznie pokazywać, czego słuchasz na Spotify.",
						style = MaterialTheme.typography.bodyMedium,
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

					SectionTitle("Ręczny status")
					Text(
						text = "Uniwersalny fallback dla serwisów bez integracji (Apple Music, YouTube Music, Tidal…).",
						style = MaterialTheme.typography.bodyMedium,
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
					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp),
					) {
						OutlinedButton(onClick = viewModel::clearManual, enabled = !state.isBusy) { Text("Wyczyść") }
						Button(onClick = viewModel::setManual, enabled = state.canSetManual) { Text("Ustaw") }
					}
				}
			}
		}
	}
}

/**
 * „Teraz gram" jak mini-odtwarzacz: okładka (albo nuta, gdy jej nie ma), tytuł,
 * wykonawca i stan odtwarzania. Zielona kropka przy „odtwarzane" mówi to samo co
 * tekst — sam kolor nie niesie informacji, której nie byłoby w słowach.
 */
@Composable
private fun CurrentStatusCard(status: NowPlayingStatus?) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Cover(imageUrl = status?.imageUrl)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "TERAZ GRAM",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (status == null) {
					Text(
						text = "Nic teraz nie gra.",
						style = MaterialTheme.typography.titleMedium,
						modifier = Modifier.padding(top = 2.dp),
					)
					Text(
						text = "Połącz Spotify albo ustaw status ręcznie poniżej.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				} else {
					Text(
						text = status.title,
						style = MaterialTheme.typography.titleLarge,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.padding(top = 2.dp),
					)
					val artist = status.artist
					if (!artist.isNullOrBlank()) {
						Text(
							text = artist,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						modifier = Modifier.padding(top = 6.dp),
					) {
						if (status.playing) {
							Box(
								modifier = Modifier
									.size(8.dp)
									.clip(CircleShape)
									.background(MaterialTheme.colorScheme.primary),
							)
						}
						Text(
							text = "${if (status.playing) "odtwarzane" else "wstrzymane"} · ${status.providerLabel}",
							style = MaterialTheme.typography.labelMedium,
							color = if (status.playing) {
								MaterialTheme.colorScheme.primary
							} else {
								MaterialTheme.colorScheme.onSurfaceVariant
							},
						)
					}
				}
			}
		}
	}
}

/** Okładka albumu 72 dp; bez adresu — nuta na ciemnozielonym tle. */
@Composable
private fun Cover(imageUrl: String?) {
	val shape = RoundedCornerShape(6.dp)
	if (!imageUrl.isNullOrBlank()) {
		AsyncImage(
			model = imageUrl,
			// Ozdoba: tytuł i wykonawca stoją obok.
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.size(72.dp)
				.clip(shape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		)
	} else {
		Box(
			modifier = Modifier
				.size(72.dp)
				.clip(shape)
				.background(MaterialTheme.colorScheme.primaryContainer),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				Icons.Outlined.MusicNote,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimaryContainer,
				modifier = Modifier.size(32.dp),
			)
		}
	}
}
