package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
	val userId: String,
	val displayName: String,
	val bio: String? = null,
	val avatarUrl: String? = null,
)

@Serializable
data class Genre(
	val id: String,
	val name: String,
)

@Serializable
data class PublicProfile(
	val userId: String,
	val displayName: String,
	val bio: String? = null,
	val avatarUrl: String? = null,
	val favoriteGenres: List<Genre> = emptyList(),
)

@Serializable
data class UpdateProfileRequest(
	val displayName: String,
	val bio: String? = null,
)

@Serializable
data class SetFavoriteGenresRequest(val genreIds: Set<String>)
