package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatePostRequest(
	val content: String,
	val imageKey: String? = null,
)

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
	val communityId: String? = null,
	val imageUrl: String? = null,
)

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
	val last: Boolean get() = page.number + 1 >= page.totalPages
}

@Serializable
data class PageMetadata(
	val size: Int,
	val number: Int,
	val totalElements: Long,
	val totalPages: Int,
)

@Serializable
data class PostImage(val key: String, val url: String)
