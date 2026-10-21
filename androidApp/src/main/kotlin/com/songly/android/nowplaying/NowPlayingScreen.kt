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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.songly.android.ui.BackButton
import com.songly.android.ui.EmptyState
import com.songly.android.ui.NavigationRow
import com.songly.android.ui.SonglyMark
import com.songly.android.ui.TabTopBar
import com.songly.android.ui.TeardropShape
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.util.RelativeTime

/**
 * Zakładka „Muzyka" (Compose, natywny UI — §14): co teraz gram, czego słuchają
 * znajomi, integracja ze Spotify (OAuth przez przeglądarkę) i uniwersalny ręczny
 * status. Stan z [NowPlayingViewModel]; dane z modułu now-playing przez `shared`.
 *
 * Na wierzchu jest tylko to, na co się patrzy (duża okładka i utwór) i dwie
 * akcje: Spotify albo status ręczny. Formularz ręcznego statusu otwiera się w
 * arkuszu od dołu — wcześniej cztery pola wisiały na ekranie na stałe, choć
 * używa się ich od święta. Odświeża się gestem w dół, jak każda lista w aplikacji.
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
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(Unit) {
		viewModel.load()
	}
	LaunchedEffect(state.statusMessage) {
		val message = state.statusMessage ?: return@LaunchedEffect
		viewModel.statusMessageShown()
		snackbarHostState.showSnackbar(message)
	}

	if (state.editingManual) {
		ManualStatusSheet(state = state, viewModel = viewModel)
	}

	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			TabTopBar(
				title = "Muzyka",
				navigationIcon = { if (onBack != null) BackButton(onBack) },
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
	) { padding ->
		PullToRefreshBox(
			isRefreshing = state.isRefreshing,
			onRefresh = viewModel::refresh,
			modifier = Modifier.fillMaxSize().padding(padding),
		) {
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
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					NowPlayingHero(status = state.status)

					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						if (state.spotifyConnected) {
							OutlinedButton(
								onClick = viewModel::disconnectSpotify,
								enabled = !state.isBusy,
								modifier = Modifier.weight(1f),
							) {
								Text("Rozłącz Spotify")
							}
						} else {
							Button(
								onClick = {
									viewModel.connectSpotify { url ->
										context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
									}
								},
								enabled = !state.isBusy,
								modifier = Modifier.weight(1f),
							) {
								Text("Połącz Spotify")
							}
						}
						OutlinedButton(
							onClick = viewModel::openManualEditor,
							enabled = !state.isBusy,
							modifier = Modifier.weight(1f),
						) {
							Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
							Text("Wpisz ręcznie", modifier = Modifier.padding(start = 8.dp))
						}
					}
					Text(
						text = "Bez Spotify? Wpisz utwór ręcznie — zadziała z Apple Music, YouTube Music, " +
							"Tidal i każdym innym serwisem.",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					NavigationRow(
						icon = Icons.Outlined.Headphones,
						title = "Znajomi teraz",
						subtitle = "Zobacz na żywo, czego słuchają Twoi znajomi",
						onClick = onOpenFriendsPresence,
					)

					ShareWithFriendsRow(
						checked = state.shareWithFriends,
						enabled = !state.isBusy,
						onChange = viewModel::onShareWithFriendsChange,
					)
				}
			}
		}
	}
}

/**
 * „Teraz gram" jak ekran odtwarzacza: duża okładka w kształcie kropli (albo
 * wyciszony znak Songly, gdy nic nie gra), tytuł, wykonawca i stan. Zielona kropka
 * przy „odtwarzane" mówi to samo co tekst — sam kolor nie niesie informacji,
 * której nie byłoby w słowach.
 */
@Composable
private fun NowPlayingHero(status: NowPlayingStatus?) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.background(
					Brush.verticalGradient(
						listOf(MaterialTheme.colorScheme.primaryContainer, Color.Transparent),
					),
				)
				.padding(horizontal = 20.dp, vertical = 24.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Cover(imageUrl = status?.imageUrl, playing = status != null)
			Text(
				text = "TERAZ GRAM",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 20.dp),
			)
			if (status == null) {
				Text(
					text = "Nic teraz nie gra",
					style = MaterialTheme.typography.headlineSmall,
					modifier = Modifier.padding(top = 4.dp).semantics { heading() },
				)
				Text(
					text = "Połącz Spotify albo wpisz utwór ręcznie — znajomi zobaczą, czego słuchasz.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(top = 4.dp),
				)
			} else {
				Text(
					text = status.title,
					style = MaterialTheme.typography.headlineSmall,
					textAlign = TextAlign.Center,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(top = 4.dp).semantics { heading() },
				)
				val artist = status.artist
				if (!artist.isNullOrBlank()) {
					Text(
						text = artist,
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				PlaybackLine(status = status)
			}
		}
	}
}

/** „odtwarzane · Spotify · 2 min temu" — stan, źródło i świeżość statusu w jednej linii. */
@Composable
private fun PlaybackLine(status: NowPlayingStatus) {
	val color = if (status.playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
	val parts = listOfNotNull(
		if (status.playing) "odtwarzane" else "wstrzymane",
		status.providerLabel,
		// Spotify odpytujemy co pół minuty, a ręczny status stoi, aż ktoś go zmieni —
		// bez wieku statusu „Bohemian Rhapsody" sprzed doby wyglądałoby na bieżące.
		status.fetchedAt?.let { "zaktualizowano ${RelativeTime.format(it)}" },
	)
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier.padding(top = 10.dp),
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
			text = parts.joinToString(" · "),
			style = MaterialTheme.typography.labelMedium,
			color = color,
			textAlign = TextAlign.Center,
		)
	}
}

/** Okładka w kształcie kropli; bez adresu — znak Songly, zielony gdy coś gra, szary gdy nic. */
@Composable
private fun Cover(imageUrl: String?, playing: Boolean) {
	if (!imageUrl.isNullOrBlank()) {
		AsyncImage(
			model = imageUrl,
			// Ozdoba: tytuł i wykonawca stoją pod spodem.
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.size(168.dp)
				.clip(TeardropShape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		)
	} else if (playing) {
		SonglyMark(size = 168.dp)
	} else {
		SonglyMark(
			size = 168.dp,
			color = MaterialTheme.colorScheme.surfaceVariant,
			barColor = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

/** Cały wiersz jest przełącznikiem — TalkBack czyta etykietę razem ze stanem. */
@Composable
private fun ShareWithFriendsRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
			.padding(vertical = 8.dp, horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(text = "Pokazuj status znajomym", style = MaterialTheme.typography.titleMedium)
			Text(
				text = "Wyłączenie chowa Twój status wszędzie: na liście znajomych i w ich strumieniu na żywo.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Switch(checked = checked, onCheckedChange = null, enabled = enabled)
	}
}

/**
 * Ręczny status w arkuszu od dołu: uniwersalny fallback dla serwisów bez
 * integracji. Zamyka go dopiero udany zapis ([NowPlayingUiState.editingManual]),
 * więc błąd zostawia wpisane pola na miejscu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualStatusSheet(state: NowPlayingUiState, viewModel: NowPlayingViewModel) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = viewModel::dismissManualEditor,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = "Czego słuchasz?",
				style = MaterialTheme.typography.headlineSmall,
				modifier = Modifier.semantics { heading() },
			)
			ManualField(value = state.title, onChange = viewModel::onTitleChange, label = "Tytuł utworu", enabled = !state.isBusy)
			ManualField(value = state.artist, onChange = viewModel::onArtistChange, label = "Wykonawca", enabled = !state.isBusy)
			ManualField(value = state.album, onChange = viewModel::onAlbumChange, label = "Album", enabled = !state.isBusy)
			ManualField(
				value = state.source,
				onChange = viewModel::onSourceChange,
				label = "Serwis (np. YouTube Music)",
				enabled = !state.isBusy,
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.fillMaxWidth(),
			) {
				// Czyści status na serwerze, nie tylko pola — tak jak robił to przycisk na ekranie.
				TextButton(onClick = viewModel::clearManual, enabled = !state.isBusy) { Text("Wyczyść status") }
				Box(modifier = Modifier.weight(1f))
				Button(onClick = viewModel::setManual, enabled = state.canSetManual) { Text("Ustaw") }
			}
		}
	}
}

@Composable
private fun ManualField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
	OutlinedTextField(
		value = value,
		onValueChange = onChange,
		label = { Text(label) },
		singleLine = true,
		enabled = enabled,
		modifier = Modifier.fillMaxWidth(),
	)
}
