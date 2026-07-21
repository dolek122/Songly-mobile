package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Spójny format błędu z backendu (`com.songly.common.web.ApiError`). Każda ścieżka
 * błędu na serwerze (walidacja, 401, 403, 404, 429, 500) zwraca ten sam kształt,
 * więc po stronie mobilnej wystarczy jeden parser.
 */
@Serializable
data class ApiError(
	val code: String,
	val message: String,
	val timestamp: String,
	val path: String,
)

/** Wyjątek niosący [ApiError] i status HTTP — rzucany przez [com.songly.shared.network.SonglyApiClient]. */
class SonglyApiException(
	val status: Int,
	val error: ApiError?,
	message: String,
) : Exception(message)
