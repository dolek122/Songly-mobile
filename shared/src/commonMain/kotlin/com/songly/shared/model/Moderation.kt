package com.songly.shared.model

import kotlinx.serialization.Serializable

val REPORT_REASONS = listOf("SPAM", "HARASSMENT", "HATE_SPEECH", "ILLEGAL_CONTENT", "OTHER")

@Serializable
data class ReportPostRequest(val reason: String, val details: String? = null)

@Serializable
data class PostReport(
	val id: String,
	val post: Post,
	val reporterId: String,
	val reporterDisplayName: String? = null,
	val reason: String,
	val details: String? = null,
	val createdAt: String,
	val reportCount: Long = 1,
	val reporterUpheldCount: Int = 0,
	val reporterDismissedCount: Int = 0,
)

@Serializable
data class CommunityBan(
	val userId: String,
	val displayName: String? = null,
	val avatarUrl: String? = null,
	val reason: String? = null,
	val bannedBy: String? = null,
	val createdAt: String,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealText: String? = null,
	val appealedAt: String? = null,
) {
	val hasPendingAppeal: Boolean
		get() = appealStatus == "PENDING"
}

@Serializable
data class BanMemberRequest(val reason: String? = null, val durationDays: Int? = null)

@Serializable
data class SetMemberRoleRequest(val role: String)

@Serializable
data class BanAppealRequest(val text: String)

@Serializable
data class ModerationLogEntry(
	val action: String,
	val actorId: String? = null,
	val subjectId: String? = null,
	val communityId: String? = null,
	val occurredAt: String,
	val expiresAt: String? = null,
	val reportCount: Int? = null,
)
