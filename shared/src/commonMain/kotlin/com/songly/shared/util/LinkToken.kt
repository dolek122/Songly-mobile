package com.songly.shared.util

object LinkToken {

	private val TOKEN_PARAM = Regex("""[?&]token=([A-Za-z0-9_-]+)""")
	private val BARE_TOKEN = Regex("""[A-Za-z0-9_-]+""")

	fun extract(input: String): String? {
		val trimmed = input.trim()
		if (trimmed.isEmpty()) return null
		TOKEN_PARAM.find(trimmed)?.let { return it.groupValues[1] }
		return trimmed.takeIf { BARE_TOKEN.matches(it) }
	}
}
