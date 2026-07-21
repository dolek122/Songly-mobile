package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Status „teraz słucham" (backend: nowplaying.dto.NowPlayingStatusResponse).
 * provider to np. "spotify" albo "manual". Pola opisowe bywają puste.
 */
@Serializable
data class NowPlayingStatus(
	val provider: String,
	val title: String,
	val artist: String? = null,
	val album: String? = null,
	val imageUrl: String? = null,
	val playing: Boolean = false,
	val fetchedAt: String? = null,
)

/**
 * Ręczne ustawienie „teraz słucham" (backend: nowplaying.dto.ManualNowPlayingRequest).
 * title wymagany (max 200), artist/album max 200, source (nazwa serwisu) max 60.
 */
@Serializable
data class ManualNowPlayingRequest(
	val title: String,
	val artist: String? = null,
	val album: String? = null,
	val source: String? = null,
)

/** Odpowiedź na rozpoczęcie łączenia ze Spotify (backend: nowplaying.dto.ConnectResponse). */
@Serializable
data class ConnectResponse(val authorizeUrl: String)
