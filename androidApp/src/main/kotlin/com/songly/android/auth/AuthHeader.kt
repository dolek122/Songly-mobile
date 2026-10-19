package com.songly.android.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Znak aplikacji nad formularzami logowania, rejestracji i resetu hasła: zielone
 * kółko z korektorem i nazwa. Ten sam na każdym z tych ekranów, żeby przejście
 * między nimi nie wyglądało jak wejście do innej aplikacji.
 */
@Composable
internal fun SonglyLogo(modifier: Modifier = Modifier) {
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(
			modifier = Modifier
				.size(72.dp)
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.primary),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				Icons.Filled.GraphicEq,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimary,
				modifier = Modifier.size(40.dp),
			)
		}
		Text(
			text = "Songly",
			style = MaterialTheme.typography.headlineLarge,
			modifier = Modifier.semantics { heading() },
		)
	}
}
