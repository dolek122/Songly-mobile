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
