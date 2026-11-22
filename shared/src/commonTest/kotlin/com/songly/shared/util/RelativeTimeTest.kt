package com.songly.shared.util

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelativeTimeTest {

	private val now = Instant.parse("2026-07-24T12:00:00Z")

	private fun ago(iso: String) = RelativeTime.format(iso, now)

	@Test
	fun subMinute_isJustNow() {
		assertEquals("przed chwilą", ago("2026-07-24T11:59:30Z"))
		assertEquals("przed chwilą", ago("2026-07-24T12:00:00Z"))
	}

	@Test
	fun future_clockSkew_isTreatedAsJustNow() {
		assertEquals("przed chwilą", ago("2026-07-24T12:05:00Z"))
	}

	@Test
	fun minutes() {
		assertEquals("5 min temu", ago("2026-07-24T11:55:00Z"))
		assertEquals("59 min temu", ago("2026-07-24T11:01:00Z"))
	}

	@Test
	fun hours() {
		assertEquals("1 godz. temu", ago("2026-07-24T11:00:00Z"))
		assertEquals("3 godz. temu", ago("2026-07-24T09:00:00Z"))
		assertEquals("23 godz. temu", ago("2026-07-23T13:00:00Z"))
	}

	@Test
	fun yesterday_isSingular() {
		assertEquals("wczoraj", ago("2026-07-23T12:00:00Z"))
	}

	@Test
	fun days() {
		assertEquals("4 dni temu", ago("2026-07-20T12:00:00Z"))
		assertEquals("6 dni temu", ago("2026-07-18T12:00:00Z"))
	}

	@Test
	fun olderThanWeek_fallsBackToAbsoluteDate() {
		val result = ago("2026-07-17T12:00:00Z")
		assertTrue(
			Regex("""\d{2}\.\d{2}\.\d{4}""").matches(result),
			"Oczekiwano daty dd.MM.yyyy, było: $result",
		)
	}

	@Test
	fun unparseableInput_isReturnedUnchanged() {
		assertEquals("nie-data", ago("nie-data"))
		assertEquals("", ago(""))
	}
}
