package com.songly.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.songly.android.auth.LoginScreen
import com.songly.android.auth.LoginViewModel
import com.songly.shared.config.ApiConfig
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/**
 * Punkt wejścia aplikacji Androida. UI natywny (Jetpack Compose, §14); logika
 * sieci i sesji żyje w module `shared` (KMP), współdzielona z iOS.
 *
 * [SonglyApiClient] tworzymy raz na Activity (trzyma sesję i klienta HTTP) i
 * zamykamy w [onDestroy]. Docelowo wstrzykniemy go przez DI zamiast trzymać w
 * Activity — na tym etapie prostota wygrywa.
 */
class MainActivity : ComponentActivity() {

	private val apiClient: SonglyApiClient by lazy {
		SonglyApiClient(ApiConfig.LOCAL_ANDROID_EMULATOR)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			MaterialTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					AppRoot(apiClient)
				}
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		apiClient.close()
	}
}

@Composable
private fun AppRoot(client: SonglyApiClient) {
	var loggedIn by remember { mutableStateOf(false) }

	if (loggedIn) {
		LoggedInScreen(client = client, onLoggedOut = { loggedIn = false })
	} else {
		val loginViewModel: LoginViewModel = viewModel(
			factory = viewModelFactory {
				initializer { LoginViewModel(client) }
			},
		)
		LoginScreen(viewModel = loginViewModel, onLoggedIn = { loggedIn = true })
	}
}

/**
 * Tymczasowy ekran po zalogowaniu — potwierdza, że sesja działa. Zastąpi go feed,
 * gdy zbudujemy pierwszy realny ekran treści.
 */
@Composable
private fun LoggedInScreen(client: SonglyApiClient, onLoggedOut: () -> Unit) {
	val scope = rememberCoroutineScope()
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(text = "Zalogowano ✓", style = MaterialTheme.typography.headlineMedium)
		Text(
			text = "Sesja aktywna. Feed pojawi się tutaj.",
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
		)
		Button(onClick = {
			scope.launch {
				client.logout()
				onLoggedOut()
			}
		}) {
			Text("Wyloguj")
		}
	}
}
