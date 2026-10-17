package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Moderacja społeczności (backend §8, ADR 0011–0030). Enumy backendu trzymamy
 * jako String, jak w pozostałych modelach — UI mapuje je na tekst, a nowa wartość
 * po stronie serwera nie wywraca deserializacji.
 */

/** Powody zgłoszenia — kolejność jak w UI. */
val REPORT_REASONS = listOf("SPAM", "HARASSMENT", "HATE_SPEECH", "ILLEGAL_CONTENT", "OTHER")

/** Zgłoszenie posta (backend: content.dto.ReportPostRequest; opis max 500). */
@Serializable
data class ReportPostRequest(val reason: String, val details: String? = null)

/**
 * Pozycja kolejki zgłoszeń (backend: content.dto.PostReportResponse).
 *
 * [reportCount] to liczba zgłoszeń tego posta. [reporterUpheldCount] i
 * [reporterDismissedCount] to historia zgłaszającego **w tej społeczności** z
 * ostatnich 90 dni — waga do oceny, nie wyrok (ADR 0025, 0030).
 */
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

/**
 * Ban w społeczności z perspektywy moderacji (backend: community.dto.CommunityBanResponse).
 * [expiresAt] `null` = bezterminowy; [appealStatus] `null` = bez odwołania.
 */
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

/** Nałożenie bana (backend: community.dto.BanMemberRequest). [durationDays] `null` = bezterminowo. */
@Serializable
data class BanMemberRequest(val reason: String? = null, val durationDays: Int? = null)

/** Zmiana roli członka (backend: community.dto.SetMemberRoleRequest) — `MODERATOR` albo `MEMBER`. */
@Serializable
data class SetMemberRoleRequest(val role: String)

/** Odwołanie od bana (backend: community.dto.BanAppealRequest; max 1000 znaków). */
@Serializable
data class BanAppealRequest(val text: String)

/**
 * Wpis dziennika moderacji (backend: moderationlog.dto.ModerationLogEntryResponse).
 * Dziennik odpowiada na „czy i ile razy", nie „dlaczego" — bez powodów i treści
 * odwołań (ADR 0024). [actorId] bywa `null`, gdy konto wykonującego usunięto.
 */
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
