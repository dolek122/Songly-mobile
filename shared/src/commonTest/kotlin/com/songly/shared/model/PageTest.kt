package com.songly.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pilnuje, że mobilny model [Page] pasuje do stabilnego kontraktu `PagedModel`
 * backendu (VIA_DTO): lista pod `content`, metadane w zagnieżdżonym `page`.
 * Dodatkowo weryfikuje lokalne wyliczanie `first`/`last`, których kontrakt
 * już nie niesie.
 */
class PageTest {

	private val json = Json { ignoreUnknownKeys = true }

	private fun parse(raw: String): Page<String> = json.decodeFromString(raw)

	@Test
	fun parsesNestedPagedModelShape() {
		val page = parse(
			"""
			{"content":["a","b"],"page":{"size":20,"number":0,"totalElements":2,"totalPages":1}}
			""".trimIndent(),
		)
		assertEquals(listOf("a", "b"), page.content)
		assertEquals(0, page.number)
		assertEquals(20, page.size)
		assertEquals(2, page.totalElements)
		assertEquals(1, page.totalPages)
	}

	@Test
	fun singlePage_isBothFirstAndLast() {
		val page = parse("""{"content":["a"],"page":{"size":20,"number":0,"totalElements":1,"totalPages":1}}""")
		assertTrue(page.first)
		assertTrue(page.last)
	}

	@Test
	fun firstOfManyPages_isFirstNotLast() {
		val page = parse("""{"content":["a"],"page":{"size":1,"number":0,"totalElements":3,"totalPages":3}}""")
		assertTrue(page.first)
		assertFalse(page.last)
	}

	@Test
	fun middlePage_isNeitherFirstNorLast() {
		val page = parse("""{"content":["b"],"page":{"size":1,"number":1,"totalElements":3,"totalPages":3}}""")
		assertFalse(page.first)
		assertFalse(page.last)
	}

	@Test
	fun lastOfManyPages_isLastNotFirst() {
		val page = parse("""{"content":["c"],"page":{"size":1,"number":2,"totalElements":3,"totalPages":3}}""")
		assertFalse(page.first)
		assertTrue(page.last)
	}

	@Test
	fun emptyPage_isFirstAndLast() {
		val page = parse("""{"content":[],"page":{"size":20,"number":0,"totalElements":0,"totalPages":0}}""")
		assertTrue(page.content.isEmpty())
		assertTrue(page.first)
		assertTrue(page.last)
	}
}
