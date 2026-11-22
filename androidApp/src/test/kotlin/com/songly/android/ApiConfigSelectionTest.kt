package com.songly.android

import com.songly.shared.config.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigSelectionTest {

	@Test
	fun releaseUsesStagingOverHttpsWithoutNetworkLogging() {
		val release = SonglyApplication.apiConfigFor(debug = false)

		assertEquals(ApiConfig.STAGING.baseUrl, release.baseUrl)
		assertTrue(release.baseUrl.startsWith("https://"))
		assertFalse(release.enableNetworkLogging)
	}

	@Test
	fun debugUsesBackendOnHostMachine() {
		assertEquals(ApiConfig.LOCAL_ANDROID_EMULATOR, SonglyApplication.apiConfigFor(debug = true))
	}
}
