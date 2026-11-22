package com.songly.shared.util

sealed interface AuthLink {
	val token: String

	data class VerifyEmail(override val token: String) : AuthLink

	data class ResetPassword(override val token: String) : AuthLink

	companion object {

		private val KIND = Regex("""^songly://(verify-email|reset-password)(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

		fun parse(url: String): AuthLink? {
			val trimmed = url.trim()
			val kind = KIND.find(trimmed)?.groupValues?.get(1)?.lowercase() ?: return null
			val token = LinkToken.extract(trimmed) ?: return null
			return if (kind == "verify-email") VerifyEmail(token) else ResetPassword(token)
		}
	}
}
