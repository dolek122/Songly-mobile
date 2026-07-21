package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Komentarz do posta (backend: content.dto.CommentResponse). */
@Serializable
data class Comment(
	val id: String,
	val postId: String,
	val authorId: String,
	val authorDisplayName: String,
	val content: String,
	val createdAt: String,
)

/** Żądanie dodania komentarza (backend: content.dto.CreateCommentRequest, treść max 500 znaków). */
@Serializable
data class CreateCommentRequest(val content: String)
