package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class NowPlayingStatus(
	val provider: String,
	val title: String,
	val artist: String? = null,
	val album: String? = null,
	val imageUrl: String? = null,
	val playing: Boolean = false,
	val fetchedAt: String? = null,
) {
	val providerLabel: String
		get() = when (provider) {
			"manual" -> "wpisane ręcznie"
			"spotify" -> "Spotify"
			else -> provider
		}
}

@Serializable
data class ManualNowPlayingRequest(
	val title: String,
	val artist: String? = null,
	val album: String? = null,
	val source: String? = null,
)

@Serializable
data class ConnectResponse(val authorizeUrl: String)

@Serializable
data class CompleteConnectRequest(val ticket: String)

@Serializable
data class FriendNowPlaying(
	val userId: String,
	val displayName: String? = null,
	val avatarUrl: String? = null,
	val status: NowPlayingStatus? = null,
)

@Serializable
data class NowPlayingSettings(val shareWithFriends: Boolean)

@Serializable
data class UpdateNowPlayingSettingsRequest(val shareWithFriends: Boolean)
