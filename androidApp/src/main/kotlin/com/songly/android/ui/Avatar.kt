package com.songly.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Okrągły awatar użytkownika. Gdy [avatarUrl] jest ustawiony, ładuje obraz przez
 * Coil; w przeciwnym razie pokazuje fallback z inicjałami z [displayName]. Backend
 * niesie `avatarUrl` w większości DTO odczytowych (posty, komentarze, znajomi,
 * powiadomienia, społeczności, dopasowania), więc `null` jest normalnym przypadkiem
 * — po prostu wtedy renderujemy inicjały.
 */
@Composable
fun Avatar(
	displayName: String,
	avatarUrl: String?,
	modifier: Modifier = Modifier,
	size: Dp = 40.dp,
) {
	val shape = CircleShape
	if (!avatarUrl.isNullOrBlank()) {
		AsyncImage(
			model = avatarUrl,
			contentDescription = "Awatar: $displayName",
			contentScale = ContentScale.Crop,
			modifier = modifier
				.size(size)
				.clip(shape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		)
	} else {
		Box(
			modifier = modifier
				.size(size)
				.clip(shape)
				.background(MaterialTheme.colorScheme.primaryContainer),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = initials(displayName),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onPrimaryContainer,
			)
		}
	}
}

/** Do dwóch pierwszych liter (z pierwszych dwóch słów) nazwy; „?" gdy brak. */
private fun initials(displayName: String): String =
	displayName.trim().split(Regex("\\s+"))
		.take(2)
		.mapNotNull { it.firstOrNull()?.uppercaseChar() }
		.joinToString("")
		.ifBlank { "?" }
