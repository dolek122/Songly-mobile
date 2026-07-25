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

/**
 * Status znajomego na liście „co słuchają znajomi" i w strumieniu presence
 * (backend: nowplaying.dto.FriendNowPlayingResponse, ADR 0007).
 *
 * [status] `null` znaczy **przestał słuchać** — w strumieniu SSE to sygnał, żeby
 * zdjąć znajomego z listy grających, a nie „brak danych".
 */
@Serializable
data class FriendNowPlaying(
	val userId: String,
	val displayName: String? = null,
	val avatarUrl: String? = null,
	val status: NowPlayingStatus? = null,
)

/**
 * Widoczność własnego statusu dla znajomych (backend:
 * nowplaying.dto.NowPlayingSettingsResponse). Domyślnie włączona — wyłączenie
 * chowa status wszędzie: na liście znajomych, w strumieniu i w serwerowym pollu.
 */
@Serializable
data class NowPlayingSettings(val shareWithFriends: Boolean)

/** Zmiana widoczności statusu (backend: nowplaying.dto.UpdateNowPlayingSettingsRequest). */
@Serializable
data class UpdateNowPlayingSettingsRequest(val shareWithFriends: Boolean)
