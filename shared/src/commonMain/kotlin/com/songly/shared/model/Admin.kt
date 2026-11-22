package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AdminList(val userIds: List<String> = emptyList())

@Serializable
data class SuspendAccountRequest(val reason: String, val durationDays: Int? = null)

@Serializable
data class SuspendedAccount(
	val userId: String,
	val reason: String? = null,
	val suspendedAt: String,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealText: String? = null,
	val appealedAt: String? = null,
) {
	val hasPendingAppeal: Boolean
		get() = appealStatus == "PENDING"
}

@Serializable
data class EscalatedAppeal(
	val userId: String,
	val communityId: String,
	val communityName: String? = null,
	val reason: String? = null,
	val bannedAt: String,
	val expiresAt: String? = null,
	val appealText: String? = null,
	val appealedAt: String? = null,
	val escalatedAt: String? = null,
)

@Serializable
data class AnalyticsSummary(
	val windowDays: Int,
	val since: String,
	val counts: List<AnalyticsCount> = emptyList(),
)

@Serializable
data class AnalyticsCount(val type: String, val count: Long)

@Serializable
data class Suspension(
	val suspended: Boolean,
	val reason: String? = null,
	val suspendedAt: String? = null,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealedAt: String? = null,
)

@Serializable
data class SuspensionAppealRequest(val text: String)

@Serializable
data class ProfileSummary(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val suspended: Boolean = false,
)
