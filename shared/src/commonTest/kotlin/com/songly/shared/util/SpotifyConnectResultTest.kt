package com.songly.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyConnectResultTest {

	@Test
	fun recognizesEveryResult() {
		assertEquals(
			SpotifyConnectResult.Authorized("t1cket_-x"),
			SpotifyConnectResult.parse("songly://now-playing/spotify?result=authorized&ticket=t1cket_-x"),
		)
		assertEquals(SpotifyConnectResult.Denied, SpotifyConnectResult.parse("songly://now-playing/spotify?result=denied"))
		assertEquals(
			SpotifyConnectResult.Failed("INVALID_OAUTH_STATE"),
			SpotifyConnectResult.parse("songly://now-playing/spotify?result=error&code=INVALID_OAUTH_STATE"),
		)
	}

	@Test
	fun authorizedWithoutTicketIsAFailure() {
		assertEquals(SpotifyConnectResult.Failed(null), SpotifyConnectResult.parse("songly://now-playing/spotify?result=authorized"))
		assertEquals(
			SpotifyConnectResult.Failed(null),
			SpotifyConnectResult.parse("songly://now-playing/spotify?result=authorized&ticket="),
		)
	}

	@Test
	fun formerConnectedResultIsUnknown() {
		assertNull(SpotifyConnectResult.parse("songly://now-playing/spotify?result=connected"))
	}

	@Test
	fun errorWithoutCodeIsStillAFailure() {
		assertEquals(SpotifyConnectResult.Failed(null), SpotifyConnectResult.parse("songly://now-playing/spotify?result=error"))
	}

	@Test
	fun toleratesCaseTrailingSlashAndParameterOrder() {
		assertEquals(
			SpotifyConnectResult.Failed("MUSIC_PROVIDER_ERROR"),
			SpotifyConnectResult.parse("SONGLY://Now-Playing/Spotify/?code=MUSIC_PROVIDER_ERROR&result=error"),
		)
	}

	@Test
	fun rejectsUnknownOrMissingResult() {
		assertNull(SpotifyConnectResult.parse("songly://now-playing/spotify?result=pending"))
		assertNull(SpotifyConnectResult.parse("songly://now-playing/spotify"))
	}

	@Test
	fun rejectsOtherLinks() {
		assertNull(SpotifyConnectResult.parse("songly://now-playing/spotifyx?result=connected"))
		assertNull(SpotifyConnectResult.parse("songly://verify-email?token=abc&result=connected"))
		assertNull(SpotifyConnectResult.parse("https://songly.app/now-playing/spotify?result=connected"))
	}
}
