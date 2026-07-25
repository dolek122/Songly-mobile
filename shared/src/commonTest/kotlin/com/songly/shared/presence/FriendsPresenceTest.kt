package com.songly.shared.presence

import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.model.NowPlayingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scalanie zmian ze strumienia SSE (ADR 0007 backendu, Etap 3) — logika trzymana
 * w `shared`, bo obie platformy muszą pokazywać dokładnie to samo.
 */
class FriendsPresenceTest {

	private fun friend(id: String, name: String?, title: String? = "Utwór") = FriendNowPlaying(
		userId = id,
		displayName = name,
		status = title?.let { NowPlayingStatus(provider = "spotify", title = it, playing = true) },
	)

	@Test
	fun addsNewlyPlayingFriend() {
		val result = listOf(friend("1", "Ada")).applyPresenceChange(friend("2", "Bartek"))

		assertEquals(listOf("Ada", "Bartek"), result.map { it.displayName })
	}

	@Test
	fun replacesStatusOfFriendAlreadyOnTheList() {
		val start = listOf(friend("1", "Ada", "Stary utwór"))

		val result = start.applyPresenceChange(friend("1", "Ada", "Nowy utwór"))

		assertEquals(1, result.size)
		assertEquals("Nowy utwór", result.single().status?.title)
	}

	/** `status == null` to kontraktowe „przestał słuchać", nie „brak danych". */
	@Test
	fun removesFriendWhoStoppedListening() {
		val start = listOf(friend("1", "Ada"), friend("2", "Bartek"))

		val result = start.applyPresenceChange(friend("2", "Bartek", title = null))

		assertEquals(listOf("Ada"), result.map { it.displayName })
	}

	@Test
	fun removingSomeoneWhoWasNotPlaying_changesNothing() {
		val start = listOf(friend("1", "Ada"))

		val result = start.applyPresenceChange(friend("9", "Ktoś", title = null))

		assertEquals(start, result)
	}

	/**
	 * Kolejność jak w `GET /now-playing/friends` — inaczej wpis skakałby po
	 * ekranie tylko dlatego, że przyszedł strumieniem, a nie z odświeżenia.
	 */
	@Test
	fun keepsListSortedByName_withUnnamedLast() {
		val start = listOf(friend("1", "Zenek"), friend("2", null))

		val result = start.applyPresenceChange(friend("3", "ala"))

		assertEquals(listOf("ala", "Zenek", null), result.map { it.displayName })
	}

	@Test
	fun reconnectDelayBacksOffAndStopsAtHalfAMinute() {
		assertEquals(1_000, reconnectDelayMillis(0))
		assertEquals(2_000, reconnectDelayMillis(1))
		assertEquals(4_000, reconnectDelayMillis(2))
		assertEquals(30_000, reconnectDelayMillis(5))
		// Kolejne próby nie mogą rosnąć w nieskończoność — po powrocie sieci
		// strumień ma wrócić w rozsądnym czasie, nie po kwadransie.
		assertEquals(30_000, reconnectDelayMillis(50))
	}

	@Test
	fun reconnectDelayIsNeverNegative_forNonsenseInput() {
		assertTrue(reconnectDelayMillis(-1) > 0)
	}
}
