package com.songly.shared.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object RelativeTime {

	private const val MINUTE = 60L
	private const val HOUR = 60L * MINUTE
	private const val DAY = 24L * HOUR
	private const val WEEK = 7L * DAY

	fun format(isoInstant: String): String = format(isoInstant, Clock.System.now())

	internal fun format(isoInstant: String, now: Instant): String {
		val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return isoInstant
		val seconds = (now - then).inWholeSeconds
		return when {
			seconds < MINUTE -> "przed chwilą"
			seconds < HOUR -> "${seconds / MINUTE} min temu"
			seconds < DAY -> "${seconds / HOUR} godz. temu"
			seconds < WEEK -> (seconds / DAY).let { if (it == 1L) "wczoraj" else "$it dni temu" }
			else -> absoluteDate(then)
		}
	}

	private fun absoluteDate(instant: Instant): String {
		val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
		val day = dt.dayOfMonth.toString().padStart(2, '0')
		val month = dt.monthNumber.toString().padStart(2, '0')
		return "$day.$month.${dt.year}"
	}
}
