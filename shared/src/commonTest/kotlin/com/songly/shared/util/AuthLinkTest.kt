package com.songly.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthLinkTest {

	@Test
	fun recognizesVerificationLink() {
		assertEquals(AuthLink.VerifyEmail("aB3_x-9"), AuthLink.parse("songly://verify-email?token=aB3_x-9"))
	}

	@Test
	fun recognizesResetLink() {
		assertEquals(AuthLink.ResetPassword("abc"), AuthLink.parse("songly://reset-password?token=abc"))
	}

	/** Klienci poczty bywają kreatywni z wielkością liter i ukośnikami. */
	@Test
	fun toleratesCaseAndTrailingSlash() {
		assertEquals(AuthLink.VerifyEmail("abc"), AuthLink.parse("SONGLY://Verify-Email/?token=abc"))
	}

	@Test
	fun rejectsLinkWithoutToken() {
		assertNull(AuthLink.parse("songly://verify-email"))
		assertNull(AuthLink.parse("songly://reset-password?token="))
	}

	/** Prefiks hosta nie wystarcza — `verify-emailx` to nie nasz link. */
	@Test
	fun rejectsOtherHostsAndSchemes() {
		assertNull(AuthLink.parse("songly://verify-emailx?token=abc"))
		assertNull(AuthLink.parse("songly://profile?token=abc"))
		assertNull(AuthLink.parse("https://songly.app/verify-email?token=abc"))
	}
}
