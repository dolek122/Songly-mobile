package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Społeczność (backend: community.dto.CommunityResponse).
 *
 * [myRole] to nazwa enuma (OWNER/MEMBER) albo `null`, gdy przeglądający nie jest
 * członkiem — stąd [isMember]/[isOwner].
 */
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
}

/** Członek społeczności (backend: community.dto.CommunityMemberResponse). */
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
}

/** Utworzenie społeczności (backend: community.dto.CreateCommunityRequest; nazwa max 100, opis max 500). */
@Serializable
data class CreateCommunityRequest(val name: String, val description: String? = null)

/** Edycja społeczności (backend: community.dto.UpdateCommunityRequest; nazwa max 100, opis max 500). */
@Serializable
data class UpdateCommunityRequest(val name: String, val description: String? = null)

/**
 * Mój status w społeczności (backend: community.dto.MembershipStatusResponse).
 *
 * [status] to `MEMBER`, `BANNED` albo `NONE`; ban wygasły backend zwraca jako
 * `NONE`. [role] i [joinedAt] są tylko przy `MEMBER`, [ban] tylko przy `BANNED`.
 */
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

/**
 * Szczegóły mojego bana (backend: community.dto.BanDetailsResponse). Bez
 * tożsamości moderatora — ban należy do społeczności (ADR 0015).
 * [expiresAt] `null` = ban bezterminowy.
 */
@Serializable
data class BanDetails(
	val reason: String? = null,
	val createdAt: String,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealedAt: String? = null,
)
