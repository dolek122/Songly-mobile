package com.songly.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.songly.shared.config.ApiConfig

/**
 * Punkt wejścia aplikacji Androida. UI jest natywny (Jetpack Compose, §14) —
 * logika sieci/domeny żyje w module `shared` (KMP), współdzielona z iOS.
 *
 * To celowo pusty szkielet: pierwszy realny ekran (logowanie) dołożymy, gdy
 * potwierdzimy URL stagingu i dołożymy warstwę sesji tokenów.
 */
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// Klient sieciowy z `shared` — na razie tylko skonfigurowany, nie wołany.
		val apiConfig = ApiConfig.LOCAL_ANDROID_EMULATOR
		setContent {
			MaterialTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					PlaceholderScreen(backendUrl = apiConfig.baseUrl)
				}
			}
		}
	}
}

@Composable
private fun PlaceholderScreen(backendUrl: String) {
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(text = "Songly", style = MaterialTheme.typography.headlineMedium)
		Text(
			text = "Szkielet mobilki gotowy.\nBackend: $backendUrl",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 12.dp),
		)
	}
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
	MaterialTheme {
		PlaceholderScreen(backendUrl = "http://10.0.2.2:8080")
	}
}
