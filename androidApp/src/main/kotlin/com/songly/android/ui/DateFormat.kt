package com.songly.android.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_TIME = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d.MM.yyyy")

/**
 * Data i godzina w strefie urządzenia — dla terminów (koniec bana), gdzie „za 3
 * dni" z [com.songly.shared.util.RelativeTime] byłoby mniej konkretne niż data.
 * Nieparsowalne wejście wraca bez zmian zamiast wywracać ekran.
 */
fun formatDateTime(iso: String): String = runCatching {
	DATE_TIME.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

/**
 * Sama data — dla stanów trwających od jakiegoś dnia („znajomi od", „w
 * społeczności od"), gdzie godzina nic nie wnosi. Nieparsowalne wejście wraca bez
 * zmian.
 */
fun formatDate(iso: String): String = runCatching {
	DATE.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)
