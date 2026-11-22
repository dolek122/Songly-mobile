package com.songly.shared.model

import kotlinx.serialization.Serializable

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

@Serializable
data class UnreadCount(val count: Long)

@Serializable
data class MailPreferences(val decisionsAboutMe: Boolean, val moderationQueue: Boolean)

@Serializable
data class UpdateMailPreferencesRequest(val decisionsAboutMe: Boolean, val moderationQueue: Boolean)

@Serializable
data class RegisterPushDeviceRequest(val token: String, val platform: String)

@Serializable
data class RemovePushDeviceRequest(val token: String)
