package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Powiadomienie (backend: notification.dto.NotificationResponse).
 *
 * [type] to nazwa enuma z backendu (POST_LIKED, POST_COMMENTED, POST_SHARED,
 * FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED, COMMUNITY_MEMBER_JOINED) —
 * trzymamy ją jako String (jak status w [FriendRequest]), a UI mapuje na tekst.
 * [readAt] jest `null` dla nieprzeczytanych. [actorDisplayName] bywa `null`, gdy
 * profil autora zniknął — UI pokazuje wtedy fallback.
 */
@Serializable
data class Notification(
	val id: String,
	val type: String,
	val actorId: String,
	val actorDisplayName: String? = null,
	val actorAvatarUrl: String? = null,
	val resourceId: String,
	val createdAt: String,
	val readAt: String? = null,
) {
	val isUnread: Boolean
		get() = readAt == null
}

/** Liczba nieprzeczytanych powiadomień (backend: notification.dto.UnreadCountResponse). */
@Serializable
data class UnreadCount(val count: Long)

/**
 * Które powiadomienia wychodzą też e-mailem (backend: notification.dto.MailPreferencesResponse,
 * ADR 0020). Dotyczy wyłącznie poczty — skrzynki w aplikacji nie da się wyłączyć.
 * Dwie kategorie odpowiadają rolom: [decisionsAboutMe] to decyzje moderacji wobec
 * mnie, [moderationQueue] to wezwania do kolejki (zgłoszenia, odwołania).
 */
@Serializable
data class MailPreferences(val decisionsAboutMe: Boolean, val moderationQueue: Boolean)

/**
 * Zmiana preferencji poczty (backend: notification.dto.UpdateMailPreferencesRequest).
 * Oba pola obowiązkowe — `PUT` zastępuje ustawienia w całości.
 */
@Serializable
data class UpdateMailPreferencesRequest(val decisionsAboutMe: Boolean, val moderationQueue: Boolean)

/**
 * Rejestracja urządzenia push (backend: notification.dto.RegisterPushDeviceRequest,
 * ADR 0040). [platform]: `ANDROID` albo `IOS`.
 */
@Serializable
data class RegisterPushDeviceRequest(val token: String, val platform: String)

/** Wyrejestrowanie urządzenia push (backend: notification.dto.RemovePushDeviceRequest). */
@Serializable
data class RemovePushDeviceRequest(val token: String)
