package com.songly.shared.paging

import com.songly.shared.model.Page

/**
 * Jedna doczytywana lista: pozycje plus wszystko, co UI musi wiedzieć, żeby
 * pokazać stopkę i nie wysłać dwóch żądań o tę samą stronę.
 *
 * Powstało dla ekranu znajomych, który trzyma **cztery** niezależnie stronicowane
 * listy (znajomi, zaproszenia otrzymane i wysłane, zablokowani). Trzymanie tego
 * w płaskich polach stanu znaczyłoby dwadzieścia pól i cztery kopie tej samej
 * arytmetyki stron. Ekrany z jedną listą (feed, komentarze) zostają przy płaskich
 * polach — tam kontener niczego nie upraszcza.
 *
 * Logika siedzi w `shared`, nie w ViewModelu, bo iOS będzie stronicował te same
 * listy dokładnie tak samo — i bo dzięki temu da się ją przetestować bez
 * urządzenia.
 */
data class PagedSection<T>(
	val items: List<T> = emptyList(),
	val nextPage: Int = 0,
	val endReached: Boolean = false,
	val isLoadingMore: Boolean = false,
	val appendError: String? = null,
) {

	/**
	 * Czy warto prosić o kolejną stronę. Po błędzie doczytywania **nie** wracamy
	 * sami — użytkownik decyduje przyciskiem „Ponów", inaczej zerwana sieć
	 * zamieniłaby się w pętlę nieudanych żądań.
	 */
	val canLoadMore: Boolean
		get() = !endReached && !isLoadingMore && appendError == null

	fun loadingMore(): PagedSection<T> = copy(isLoadingMore = true, appendError = null)

	/** Pierwsza strona: zastępuje zawartość i zeruje stan doczytywania. */
	fun replaceWith(page: Page<T>): PagedSection<T> = PagedSection(
		items = page.content,
		nextPage = page.number + 1,
		endReached = page.last,
	)

	/** Kolejna strona: dokłada na koniec. */
	fun append(page: Page<T>): PagedSection<T> = copy(
		items = items + page.content,
		nextPage = page.number + 1,
		endReached = page.last,
		isLoadingMore = false,
		appendError = null,
	)

	/** Nieudane doczytywanie: lista zostaje, stopka pokazuje błąd i „Ponów". */
	fun failed(message: String): PagedSection<T> = copy(isLoadingMore = false, appendError = message)

	/** Ponowna próba po błędzie — bez tego [canLoadMore] blokowałby żądanie. */
	fun retrying(): PagedSection<T> = copy(isLoadingMore = true, appendError = null)

	fun removeIf(predicate: (T) -> Boolean): PagedSection<T> = copy(items = items.filterNot(predicate))
}
