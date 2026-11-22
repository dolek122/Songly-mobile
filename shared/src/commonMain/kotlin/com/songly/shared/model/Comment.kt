package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
	val id: String,
	val postId: String,
	val authorId: String,
	val authorDisplayName: String,
	val authorAvatarUrl: String? = null,
	val content: String,
	val createdAt: String,
)

@Serializable
data class CreateCommentRequest(val content: String)
