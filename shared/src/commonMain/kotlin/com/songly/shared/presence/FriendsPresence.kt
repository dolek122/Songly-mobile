package com.songly.shared.presence

import com.songly.shared.model.FriendNowPlaying

/**
 * Migawka „co znajomi teraz grają" dla UI (ADR 0007 backendu, Etap 3).
 *
 * [live] mówi, czy strumień jest podłączony. Rozłączeni **nie czyścimy listy** —
 * ostatnio znane statusy zostają na ekranie z widocznym oznaczeniem, bo pusta
 * lista przy chwilowej utracie sieci wyglądałaby jak „nikt nic nie słucha".
 */
data class FriendsPresence(
	val friends: List<FriendNowPlaying> = emptyList(),
	val live: Boolean = false,
)

/**
 * Nakłada pojedynczą zmianę ze strumienia na aktualną listę.
 *
 * Kontrakt backendu: `status == null` znaczy „przestał słuchać", więc znajomy
 * znika z listy. Każda inna zmiana to wstawienie albo podmiana wpisu — kolejność
 * trzymamy po nazwie, tak samo jak `GET /now-playing/friends`, żeby wpis nie
 * skakał po ekranie tylko dlatego, że przyszedł strumieniem, a nie z odświeżenia.
 */
fun List<FriendNowPlaying>.applyPresenceChange(change: FriendNowPlaying): List<FriendNowPlaying> {
	val withoutSubject = filterNot { it.userId == change.userId }
	if (change.status == null) {
		return withoutSubject
	}
	return (withoutSubject + change).sortedWith(
		compareBy(nullsLast<String>()) { it.displayName?.lowercase() },
	)
}

/**
 * Odstęp przed kolejną próbą połączenia: 1s, 2s, 4s… do 30s.
 *
 * Backoff, nie stały interwał — gdy backend leży albo telefon jest bez zasięgu,
 * dobijanie się co sekundę wypala baterię i nic nie daje. Górny limit trzyma
 * powrót do żywego strumienia w rozsądnym czasie po tym, jak sieć wróci.
 */
fun reconnectDelayMillis(attempt: Int): Long {
	val capped = attempt.coerceIn(0, MAX_BACKOFF_SHIFT)
	return (INITIAL_RECONNECT_DELAY_MILLIS shl capped).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
}

private const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
