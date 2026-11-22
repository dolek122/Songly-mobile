package com.songly.android

import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.runBlocking

class TestSonglyApplication : SonglyApplication() {

	override fun createClient(): SonglyApiClient = backend.client().also { client ->
		if (!startLoggedIn) runBlocking { client.logout() }
	}

	companion object {
		var backend = FakeBackend()
		var startLoggedIn = true
	}
}
