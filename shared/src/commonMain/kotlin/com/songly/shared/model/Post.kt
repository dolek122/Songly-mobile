package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Żądanie utworzenia posta (backend: content.dto.CreatePostRequest, treść max 1000 znaków). */
@Serializable
data class CreatePostRequest(val content: String)

/** Post z feedu (backend: content.dto.PostResponse). */
@Serializable
data class Post(
	val id: String,
	val authorId: String,
	val authorDisplayName: String,
	val authorAvatarUrl: String? = null,
	val content: String,
	val createdAt: String,
	val likeCount: Long,
	val likedByMe: Boolean,
	val commentCount: Long,
	val shareCount: Long,
	val sharedByMe: Boolean,
)

/**
 * Strona wyników — backend paginuje wszystkie listy (Claude.md §6: `?page=&size=`).
 * Backend serializuje strony jako stabilne `PagedModel` (VIA_DTO): lista pod
 * [content], a metadane w zagnieżdżonym obiekcie [page]. `first`/`last` nie ma
 * w kontrakcie — wyliczamy je lokalnie z numeru strony i liczby stron.
 */
@Serializable
data class Page<T>(
	val content: List<T>,
	val page: PageMetadata,
) {
	val number: Int get() = page.number
	val size: Int get() = page.size
	val totalElements: Long get() = page.totalElements
	val totalPages: Int get() = page.totalPages
	val first: Boolean get() = page.number == 0
	// Odwzorowuje Spring: last = brak następnej strony. Dla pustego wyniku
	// (totalPages = 0) daje true, tak samo jak `PageImpl`.
	val last: Boolean get() = page.number + 1 >= page.totalPages
}

/** Metadane strony z `PagedModel` backendu (klucz `page`). */
@Serializable
data class PageMetadata(
	val size: Int,
	val number: Int,
	val totalElements: Long,
	val totalPages: Int,
)
