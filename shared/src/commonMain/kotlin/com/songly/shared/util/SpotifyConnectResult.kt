package com.songly.shared.util

/**
 * Powrót z OAuth Spotify (backend ADR 0038): callback backendu przekierowuje
 * przeglądarkę na `songly://now-playing/spotify?result=authorized|denied|error`,
 * a przy `error` dokłada kod błędu w `code`.
 *
 * `authorized` jeszcze niczego nie łączy (backend ADR 0050): niesie jednorazowy
 * bilet, którym aplikacja kończy łączenie przez
 * [com.songly.shared.network.SonglyApiClient.completeSpotifyConnect]. Backend
 * przypina Spotify tylko do konta, które łączenie zaczęło, więc link podesłany
 * przez kogoś innego nie przypnie Spotify tego użytkownika do cudzego konta.
 *
 * Zwraca `null` dla każdego innego adresu i dla nieznanego `result` — nowy wynik
 * dodany po stronie backendu nie może udawać żadnego ze znanych.
 */
sealed interface SpotifyConnectResult {

	/** Zgoda w Spotify wyrażona — [ticket] trzeba jeszcze oddać backendowi. */
	data class Authorized(val ticket: String) : SpotifyConnectResult

	/** Użytkownik odmówił zgody w Spotify. To nie jest błąd. */
	data object Denied : SpotifyConnectResult

	data class Failed(val code: String?) : SpotifyConnectResult

	companion object {

		private val LINK = Regex("""^songly://now-playing/spotify/?(?:\?([^#]*))?(?:#.*)?$""", RegexOption.IGNORE_CASE)

		fun parse(url: String): SpotifyConnectResult? {
			val match = LINK.matchEntire(url.trim()) ?: return null
			val params = match.groupValues[1].split('&').mapNotNull { pair ->
				val name = pair.substringBefore('=')
				if (name.isEmpty()) null else name to pair.substringAfter('=', "")
			}.toMap()
			return when (params["result"]) {
				// Bez biletu nie ma czym dokończyć, więc to porażka, a nie nieznany wynik.
				"authorized" -> params["ticket"]?.ifEmpty { null }?.let(::Authorized) ?: Failed(null)
				"denied" -> Denied
				"error" -> Failed(params["code"]?.ifEmpty { null })
				else -> null
			}
		}
	}
}
