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
