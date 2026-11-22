package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
	val code: String,
	val message: String,
	val timestamp: String,
	val path: String,
)

class SonglyApiException(
	val status: Int,
	val error: ApiError?,
	message: String,
) : Exception(message)
