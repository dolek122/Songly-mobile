package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Post z feedu (backend: content.dto.PostResponse). */
@Serializable
data class Post(
	val id: String,
	val authorId: String,
	val authorDisplayName: String,
	val content: String,
	val createdAt: String,
	val likeCount: Long,
	val likedByMe: Boolean,
	val commentCount: Long,
	val shareCount: Long,
	val sharedByMe: Boolean,
)

/**
 * Strona wyników — backend paginuje wszystkie listy (Claude.md §6: `?page=&size=`)
 * w standardowym kształcie Spring Data `Page`. Tu tylko pola, których realnie
 * używa UI; resztę Ktor zignoruje dzięki `ignoreUnknownKeys`.
 */
@Serializable
data class Page<T>(
	val content: List<T>,
	val number: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val first: Boolean,
	val last: Boolean,
)
