package com.songly.android

import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.runBlocking

/**
 * Aplikacja z atrapą backendu — do testów całej `MainActivity` (np. obrotu ekranu),
 * gdzie nie da się podać klienta ekranowi z ręki. Test ustawia [backend] przed
 * uruchomieniem Activity; Robolectric tworzy świeżą aplikację dla każdego testu.
 */
class TestSonglyApplication : SonglyApplication() {

	override fun createClient(): SonglyApiClient = backend.client().also { client ->
		// Atrapa zakłada sesję z góry; test ekranów sprzed logowania ją zdejmuje.
		if (!startLoggedIn) runBlocking { client.logout() }
	}

	companion object {
		var backend = FakeBackend()
		var startLoggedIn = true
	}
}
