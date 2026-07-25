package com.songly.shared.paging

import com.songly.shared.model.Page
import com.songly.shared.model.PageMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PagedSectionTest {

	private fun page(number: Int, totalPages: Int, vararg items: String) =
		Page(items.toList(), PageMetadata(size = 2, number = number, totalElements = 4, totalPages = totalPages))

	@Test
	fun firstPageReplacesContent() {
		val section = PagedSection(items = listOf("stare"), nextPage = 7, endReached = true)

		val result = section.replaceWith(page(0, 2, "a", "b"))

		assertEquals(listOf("a", "b"), result.items)
		assertEquals(1, result.nextPage)
		assertFalse(result.endReached)
	}

	@Test
	fun nextPageAppendsAndAdvances() {
		val section = PagedSection<String>().replaceWith(page(0, 2, "a", "b"))

		val result = section.loadingMore().append(page(1, 2, "c", "d"))

		assertEquals(listOf("a", "b", "c", "d"), result.items)
		assertEquals(2, result.nextPage)
		assertTrue(result.endReached)
		assertFalse(result.isLoadingMore)
	}

	@Test
	fun lastPageStopsFurtherRequests() {
		val section = PagedSection<String>().replaceWith(page(1, 2, "c", "d"))

		assertTrue(section.endReached)
		assertFalse(section.canLoadMore)
	}

	@Test
	fun emptyResultCountsAsEnd() {
		val section = PagedSection<String>().replaceWith(page(0, 0))

		assertTrue(section.endReached)
		assertFalse(section.canLoadMore)
	}

	/** Bez tej blokady infinite scroll wysłałby kilka żądań o tę samą stronę. */
	@Test
	fun requestInFlightBlocksAnother() {
		val section = PagedSection<String>().replaceWith(page(0, 3, "a", "b")).loadingMore()

		assertFalse(section.canLoadMore)
	}

	/**
	 * Po błędzie czekamy na decyzję użytkownika. Automatyczne ponawianie przy
	 * zerwanej sieci zamieniłoby przewijanie w pętlę nieudanych żądań.
	 */
	@Test
	fun failureBlocksAutomaticRetry_butKeepsItems() {
		val section = PagedSection<String>().replaceWith(page(0, 3, "a", "b")).loadingMore().failed("brak sieci")

		assertEquals(listOf("a", "b"), section.items)
		assertEquals("brak sieci", section.appendError)
		assertFalse(section.canLoadMore)

		val retry = section.retrying()

		assertTrue(retry.isLoadingMore)
		assertEquals(null, retry.appendError)
	}

	@Test
	fun removeIfDropsMatchingItemsOnly() {
		val section = PagedSection<String>().replaceWith(page(0, 2, "a", "b"))

		assertEquals(listOf("b"), section.removeIf { it == "a" }.items)
	}
}
