package com.songly.android

import com.songly.shared.config.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wersja ze sklepu nie może celować w emulator ani pisać URL-i do Logcata. */
class ApiConfigSelectionTest {

	@Test
	fun releaseUsesStagingOverHttpsWithoutNetworkLogging() {
		val release = SonglyApplication.apiConfigFor(debug = false)

		assertEquals(ApiConfig.STAGING.baseUrl, release.baseUrl)
		// Cleartext jest w release zablokowany (nakładka manifestu tylko dla debug),
		// więc adres http oznaczałby aplikację, która nie połączy się z niczym.
		assertTrue(release.baseUrl.startsWith("https://"))
		assertFalse(release.enableNetworkLogging)
	}

	@Test
	fun debugUsesBackendOnHostMachine() {
		assertEquals(ApiConfig.LOCAL_ANDROID_EMULATOR, SonglyApplication.apiConfigFor(debug = true))
	}
}
