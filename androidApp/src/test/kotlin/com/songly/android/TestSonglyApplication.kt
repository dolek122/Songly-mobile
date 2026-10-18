package com.songly.android

import com.songly.shared.network.SonglyApiClient

/**
 * Aplikacja z atrapą backendu — do testów całej `MainActivity` (np. obrotu ekranu),
 * gdzie nie da się podać klienta ekranowi z ręki. Test ustawia [backend] przed
 * uruchomieniem Activity; Robolectric tworzy świeżą aplikację dla każdego testu.
 */
class TestSonglyApplication : SonglyApplication() {

	override fun createClient(): SonglyApiClient = backend.client()

	companion object {
		var backend = FakeBackend()
	}
}
