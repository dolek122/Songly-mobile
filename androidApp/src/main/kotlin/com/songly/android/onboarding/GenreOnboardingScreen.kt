package com.songly.android.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songly.android.ui.FilterPill
import com.songly.android.ui.SonglyMark
import com.songly.android.ui.announcedWhenChanged
import com.songly.android.ui.songlyBackdrop

/**
 * „Co lubisz słuchać?" — pierwszy ekran po zalogowaniu na konto bez ulubionych
 * gatunków. Jedno pytanie, pigułki gatunków i jeden przycisk; [onFinished] dostaje
 * `true`, gdy gatunki zapisano (wtedy jest sens od razu pokazać dopasowanych).
 *
 * Systemowy Wstecz celowo nie pomija onboardingu, tylko wychodzi z aplikacji jak z
 * każdego ekranu startowego — przypadkowy gest nie może na zawsze schować pytania.
 * Pominięciem jest wyłącznie „Pomiń".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreOnboardingScreen(
	viewModel: GenreOnboardingViewModel,
	onFinished: (saved: Boolean) -> Unit,
) {
	val state = viewModel.uiState

	LaunchedEffect(Unit) {
		viewModel.load()
	}
	LaunchedEffect(state.finished) {
		if (state.finished) onFinished(state.saved)
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.songlyBackdrop()
			.systemBarsPadding()
			.padding(horizontal = 24.dp, vertical = 8.dp),
	) {
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			TextButton(onClick = viewModel::skip, enabled = !state.isSaving) { Text("Pomiń") }
		}

		Column(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState()),
		) {
			SonglyMark(size = 56.dp, modifier = Modifier.padding(top = 8.dp))
			Text(
				text = "Co lubisz słuchać?",
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(top = 24.dp).semantics { heading() },
			)
			Text(
				text = "Wybierz gatunki, które grają u Ciebie najczęściej — po nich dopasujemy Ci ludzi " +
					"o podobnym guście. Im więcej wybierzesz, tym trafniejsze dopasowania. Zmienisz je " +
					"później w profilu.",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
			)

			when {
				state.isLoading -> CircularProgressIndicator()

				state.errorMessage != null -> {
					Text(
						text = state.errorMessage,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.error,
					)
					TextButton(onClick = viewModel::load) { Text("Spróbuj ponownie") }
				}

				else -> FlowRow(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					state.genres.forEach { genre ->
						FilterPill(
							label = genre.name,
							selected = genre.id in state.selectedIds,
							onClick = { viewModel.toggle(genre.id) },
							enabled = !state.isSaving,
						)
					}
				}
			}
		}

		if (state.saveError != null) {
			Text(
				text = state.saveError,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.padding(bottom = 8.dp).announcedWhenChanged(),
			)
		}
		Button(
			onClick = viewModel::save,
			enabled = state.canSave,
			modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
		) {
			Text(
				when {
					state.isSaving -> "Zapisywanie…"
					state.selectedIds.isEmpty() -> "Wybierz co najmniej jeden"
					else -> "Gotowe (${state.selectedIds.size})"
				},
			)
		}
	}
}
