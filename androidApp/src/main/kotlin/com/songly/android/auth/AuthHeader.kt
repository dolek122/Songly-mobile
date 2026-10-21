package com.songly.android.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.songly.android.ui.SonglyMark

/**
 * Znak aplikacji nad formularzami logowania, rejestracji i resetu hasła: zielona
 * kropla z korektorem (jak ikona aplikacji) i nazwa. Ten sam na każdym z tych
 * ekranów, żeby przejście między nimi nie wyglądało jak wejście do innej aplikacji.
 */
@Composable
internal fun SonglyLogo(modifier: Modifier = Modifier) {
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		SonglyMark(size = 72.dp)
		Text(
			text = "Songly",
			style = MaterialTheme.typography.headlineLarge,
			modifier = Modifier.semantics { heading() },
		)
	}
}
