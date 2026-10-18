package com.songly.shared.util

/**
 * Powrót z OAuth Spotify (backend ADR 0038): callback backendu przekierowuje
 * przeglądarkę na `songly://now-playing/spotify?result=connected|denied|error`,
 * a przy `error` dokłada kod błędu w `code`.
 *
 * Zwraca `null` dla każdego innego adresu i dla nieznanego `result` — nowy wynik
 * dodany po stronie backendu nie może udawać żadnego ze znanych.
 */
sealed interface SpotifyConnectResult {

	data object Connected : SpotifyConnectResult

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
				"connected" -> Connected
				"denied" -> Denied
				"error" -> Failed(params["code"]?.ifEmpty { null })
				else -> null
			}
		}
	}
}
