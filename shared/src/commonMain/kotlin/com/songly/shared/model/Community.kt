package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Community(
	val id: String,
	val name: String,
	val description: String? = null,
	val ownerId: String,
	val ownerDisplayName: String? = null,
	val ownerAvatarUrl: String? = null,
	val memberCount: Long,
	val myRole: String? = null,
	val createdAt: String,
) {
	val isMember: Boolean
		get() = myRole != null

	val isOwner: Boolean
		get() = myRole == "OWNER"

	val isModerator: Boolean
		get() = myRole == "OWNER" || myRole == "MODERATOR"
}

@Serializable
data class CommunityMember(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val role: String,
	val joinedAt: String,
) {
	val isOwner: Boolean
		get() = role == "OWNER"

	val isModerator: Boolean
		get() = role == "MODERATOR"
}

@Serializable
data class CreateCommunityRequest(val name: String, val description: String? = null)

@Serializable
data class UpdateCommunityRequest(val name: String, val description: String? = null)

@Serializable
data class MembershipStatus(
	val status: String,
	val role: String? = null,
	val joinedAt: String? = null,
	val ban: BanDetails? = null,
) {
	val isMember: Boolean
		get() = status == "MEMBER"

	val isBanned: Boolean
		get() = status == "BANNED"
}

@Serializable
data class BanDetails(
	val reason: String? = null,
	val createdAt: String,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealedAt: String? = null,
)
