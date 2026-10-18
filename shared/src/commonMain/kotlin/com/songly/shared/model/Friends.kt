package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Znajomy (backend: social.dto.FriendResponse). */
@Serializable
data class Friend(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val friendsSince: String,
)

/** Zaproszenie do znajomych (backend: social.dto.FriendRequestResponse). */
@Serializable
data class FriendRequest(
	val id: String,
	val requesterId: String,
	val requesterDisplayName: String,
	val requesterAvatarUrl: String? = null,
	val addresseeId: String,
	val addresseeDisplayName: String,
	val addresseeAvatarUrl: String? = null,
	val status: String,
	val createdAt: String,
)

/** Wysłanie zaproszenia (backend: social.dto.SendFriendRequestRequest). */
@Serializable
data class SendFriendRequestRequest(val addresseeId: String)

/**
 * Czy blokuję danego użytkownika (backend: social.dto.BlockStatusResponse, ADR 0039).
 * Tylko w moim kierunku — to, czy ktoś blokuje mnie, backend celowo ukrywa.
 */
@Serializable
data class BlockStatus(val blocked: Boolean)

/** Zablokowany użytkownik (backend: social.dto.BlockedUserResponse). */
@Serializable
data class BlockedUser(
	val userId: String,
	val displayName: String,
	val blockedAt: String,
)

/** Wynik wyszukiwania użytkownika (backend: profile.dto.UserSearchResponse). */
@Serializable
data class UserSearchResult(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
)
