package com.songly.shared.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formatowanie znaczników czasu do wyświetlenia. Żyje w `shared` (KMP), więc
 * Android i iOS pokazują czas dokładnie tak samo. Backend zwraca `Instant` jako
 * ISO-8601 (np. `2026-07-22T14:30:00Z`); tu zamieniamy go na krótki, relatywny
 * opis po polsku, a starsze zdarzenia na datę bezwzględną.
 */
object RelativeTime {

	private const val MINUTE = 60L
	private const val HOUR = 60L * MINUTE
	private const val DAY = 24L * HOUR
	private const val WEEK = 7L * DAY

	/**
	 * Zwraca relatywny opis czasu, np. „przed chwilą", „5 min temu", „3 godz. temu",
	 * „wczoraj", „4 dni temu", a dla zdarzeń starszych niż tydzień — datę `dd.MM.yyyy`.
	 * Gdy [isoInstant] jest nieparsowalny, zwraca go bez zmian (bezpieczny fallback).
	 */
	fun format(isoInstant: String): String = format(isoInstant, Clock.System.now())

	/**
	 * Wariant z wstrzykiwanym „teraz" — do testów. `internal`, żeby typ [Instant]
	 * z zależności `kotlinx-datetime` nie wyciekał do publicznego API modułu `shared`
	 * (zależność jest `implementation`, więc konsumenci jej nie widzą).
	 */
	internal fun format(isoInstant: String, now: Instant): String {
		val then = runCatching { Instant.parse(isoInstant) }.getOrNull() ?: return isoInstant
		val seconds = (now - then).inWholeSeconds
		return when {
			// Przyszłość (rozjazd zegarów) traktujemy jak „teraz".
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
