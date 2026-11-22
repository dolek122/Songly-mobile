package com.songly.shared.paging

import com.songly.shared.model.Page

data class PagedSection<T>(
	val items: List<T> = emptyList(),
	val nextPage: Int = 0,
	val endReached: Boolean = false,
	val isLoadingMore: Boolean = false,
	val appendError: String? = null,
) {

	val canLoadMore: Boolean
		get() = !endReached && !isLoadingMore && appendError == null

	fun loadingMore(): PagedSection<T> = copy(isLoadingMore = true, appendError = null)

	fun replaceWith(page: Page<T>): PagedSection<T> = PagedSection(
		items = page.content,
		nextPage = page.number + 1,
		endReached = page.last,
	)

	fun append(page: Page<T>, keyOf: (T) -> Any? = { it }): PagedSection<T> = copy(
		items = items.plusDistinct(page.content, keyOf),
		nextPage = page.number + 1,
		endReached = page.last,
		isLoadingMore = false,
		appendError = null,
	)

	fun failed(message: String): PagedSection<T> = copy(isLoadingMore = false, appendError = message)

	fun retrying(): PagedSection<T> = copy(isLoadingMore = true, appendError = null)

	fun removeIf(predicate: (T) -> Boolean): PagedSection<T> = copy(items = items.filterNot(predicate))
}

fun <T> List<T>.plusDistinct(more: List<T>, keyOf: (T) -> Any?): List<T> {
	val seen = mapTo(HashSet(), keyOf)
	return this + more.filter { seen.add(keyOf(it)) }
}
