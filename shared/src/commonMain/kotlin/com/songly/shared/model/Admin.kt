package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Administracja platformy (backend ADR 0021–0031) i stan zawieszenia własnego
 * konta (ADR 0022–0023).
 */

/** Wykaz administratorów (backend: identity.dto.AdminListResponse) — same id, bez paginacji. */
@Serializable
data class AdminList(val userIds: List<String> = emptyList())

/**
 * Zawieszenie konta (backend: identity.dto.SuspendAccountRequest). Powód jest
 * obowiązkowy i widzi go zawieszony; [durationDays] `null` = bezterminowo.
 */
@Serializable
data class SuspendAccountRequest(val reason: String, val durationDays: Int? = null)

/**
 * Pozycja wykazu zawieszeń (backend: identity.dto.SuspendedAccountResponse) — ten
 * wykaz jest zarazem kolejką odwołań (ADR 0023). Bez nazwy użytkownika: profil
 * zawieszonego jest ukryty (ADR 0026), a `identity` nie zna profili.
 */
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

/**
 * Odwołanie od bana przekazane administracji (backend: community.dto.EscalatedAppealResponse).
 * Niesie obie strony sporu — powód bana i treść odwołania (ADR 0028).
 */
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

/** Zliczenia zdarzeń w oknie (backend: analytics.dto.AnalyticsSummaryResponse) — bez PII (ADR 0004). */
@Serializable
data class AnalyticsSummary(
	val windowDays: Int,
	val since: String,
	val counts: List<AnalyticsCount> = emptyList(),
)

@Serializable
data class AnalyticsCount(val type: String, val count: Long)

/**
 * Stan mojego konta (backend: identity.dto.SuspensionResponse). Przy
 * `suspended = false` pozostałe pola są puste.
 */
@Serializable
data class Suspension(
	val suspended: Boolean,
	val reason: String? = null,
	val suspendedAt: String? = null,
	val expiresAt: String? = null,
	val appealStatus: String? = null,
	val appealedAt: String? = null,
)

/** Odwołanie od zawieszenia (backend: identity.dto.SuspensionAppealRequest; max 1000). */
@Serializable
data class SuspensionAppealRequest(val text: String)

/**
 * Nazwa i awatar dla administracji (backend: profile.dto.ProfileSummaryResponse,
 * ADR 0036) — także zawieszonych, których profil jest dla reszty ukryty.
 */
@Serializable
data class ProfileSummary(
	val userId: String,
	val displayName: String,
	val avatarUrl: String? = null,
	val suspended: Boolean = false,
)
