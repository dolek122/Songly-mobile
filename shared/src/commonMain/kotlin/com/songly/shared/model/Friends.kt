package com.songly.shared.model

import kotlinx.serialization.Serializable

/** Znajomy (backend: social.dto.FriendResponse). */
@Serializable
data class Friend(
	val userId: String,
	val displayName: String,
	val friendsSince: String,
)

/** Zaproszenie do znajomych (backend: social.dto.FriendRequestResponse). */
@Serializable
data class FriendRequest(
	val id: String,
	val requesterId: String,
	val requesterDisplayName: String,
	val addresseeId: String,
	val addresseeDisplayName: String,
	val status: String,
	val createdAt: String,
)

/** Wysłanie zaproszenia (backend: social.dto.SendFriendRequestRequest). */
@Serializable
data class SendFriendRequestRequest(val addresseeId: String)

/** Wynik wyszukiwania użytkownika (backend: profile.dto.UserSearchResponse). */
@Serializable
data class UserSearchResult(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
)
