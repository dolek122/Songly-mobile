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
 * Publiczny profil innego użytkownika (backend: profile.dto.PublicProfileResponse).
 * Do oglądania cudzego profilu — niesie dane profilowe i ulubione gatunki, ale nie
 * dane o zachowaniu (co teraz słucha) — te mają osobny model prywatności.
 */
@Serializable
data class PublicProfile(
	val userId: String,
	val displayName: String,
	val bio: String? = null,
	val avatarUrl: String? = null,
	val favoriteGenres: List<Genre> = emptyList(),
)

/**
 * Aktualizacja profilu (backend: profile.dto.UpdateProfileRequest).
 * displayName wymagany (max 80), bio max 500. Awatara tu nie ma — od ADR 0010
 * backend przyjmuje wyłącznie upload pliku (`POST /profiles/me/avatar`).
 */
@Serializable
data class UpdateProfileRequest(
	val displayName: String,
	val bio: String? = null,
)

/** Ustawienie ulubionych gatunków (backend: profile.dto.SetFavoriteGenresRequest, zbiór niepusty). */
@Serializable
data class SetFavoriteGenresRequest(val genreIds: Set<String>)
