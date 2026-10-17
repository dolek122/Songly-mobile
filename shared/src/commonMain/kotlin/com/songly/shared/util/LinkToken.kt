package com.songly.shared.util

/**
 * Wyciąga token z tego, co użytkownik wkleił: cały link z maila
 * (`…/verify-email?token=abc`) albo sam token.
 *
 * Wklejanie jest dziś jedyną drogą od maila do aplikacji — link prowadzi pod adres
 * ustawiony na serwerze (`EMAIL_VERIFICATION_URL`), a aplikacja nie przechwytuje
 * go jeszcze jako deep link. Akceptujemy oba kształty, bo kopiując link z maila
 * nikt nie będzie wycinał z niego parametru ręcznie.
 *
 * Zwraca `null` dla pustego wejścia albo linku bez parametru `token`.
 */
object LinkToken {

	// Alfabet base64url, w którym backend generuje tokeny (SecureTokens).
	private val TOKEN_PARAM = Regex("""[?&]token=([A-Za-z0-9_-]+)""")
	private val BARE_TOKEN = Regex("""[A-Za-z0-9_-]+""")

	fun extract(input: String): String? {
		val trimmed = input.trim()
		if (trimmed.isEmpty()) return null
		TOKEN_PARAM.find(trimmed)?.let { return it.groupValues[1] }
		return trimmed.takeIf { BARE_TOKEN.matches(it) }
	}
}
