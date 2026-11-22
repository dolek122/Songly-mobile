package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Friend(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val friendsSince: String,
)

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

@Serializable
data class SendFriendRequestRequest(val addresseeId: String)

@Serializable
data class BlockStatus(val blocked: Boolean)

@Serializable
data class BlockedUser(
	val userId: String,
	val displayName: String,
	val blockedAt: String,
)

@Serializable
data class UserSearchResult(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
)
