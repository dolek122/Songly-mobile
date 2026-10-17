package com.songly.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkTokenTest {

	@Test
	fun extractsTokenFromFullLink() {
		assertEquals("aB3_x-9", LinkToken.extract("http://localhost:3000/verify-email?token=aB3_x-9"))
	}

	@Test
	fun extractsTokenWhenNotFirstParameter() {
		assertEquals("abc", LinkToken.extract("https://songly.app/verify?lang=pl&token=abc&utm=mail"))
	}

	@Test
	fun acceptsBareTokenWithSurroundingWhitespace() {
		assertEquals("abc_DEF-123", LinkToken.extract("  abc_DEF-123\n"))
	}

	@Test
	fun rejectsEmptyInput() {
		assertNull(LinkToken.extract("   "))
	}

	/** Link bez tokenu to pomyłka przy kopiowaniu — nie wysyłamy całego URL-a jako tokenu. */
	@Test
	fun rejectsLinkWithoutToken() {
		assertNull(LinkToken.extract("http://localhost:3000/verify-email"))
	}
}
