package com.songly.shared.util

sealed interface SpotifyConnectResult {

	data class Authorized(val ticket: String) : SpotifyConnectResult

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
				"authorized" -> params["ticket"]?.ifEmpty { null }?.let(::Authorized) ?: Failed(null)
				"denied" -> Denied
				"error" -> Failed(params["code"]?.ifEmpty { null })
				else -> null
			}
		}
	}
}
