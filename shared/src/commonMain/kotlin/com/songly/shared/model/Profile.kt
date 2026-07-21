package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Profil użytkownika (backend: profile.dto.ProfileResponse). bio/avatarUrl bywają puste. */
@Serializable
data class Profile(
	val userId: String,
	val displayName: String,
	val bio: String? = null,
	val avatarUrl: String? = null,
)

/** Gatunek muzyczny (backend: profile.dto.GenreResponse). */
@Serializable
data class Genre(
	val id: String,
	val name: String,
)

/**
 * Aktualizacja profilu (backend: profile.dto.UpdateProfileRequest).
 * displayName wymagany (max 80), bio max 500, avatarUrl max 2048.
 */
@Serializable
data class UpdateProfileRequest(
	val displayName: String,
	val bio: String? = null,
	val avatarUrl: String? = null,
)

/** Ustawienie ulubionych gatunków (backend: profile.dto.SetFavoriteGenresRequest, zbiór niepusty). */
@Serializable
data class SetFavoriteGenresRequest(val genreIds: Set<String>)
