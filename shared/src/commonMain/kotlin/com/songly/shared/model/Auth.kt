package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Modele uwierzytelniania — odwzorowują DTO z backendu (moduł `identity`).
 * Źródłem prawdy jest kontrakt OpenAPI (`docs/api/openapi.json` w Songly-backend);
 * przy zmianie pól po stronie serwera zaktualizuj te klasy.
 */
@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Odpowiedź backendu na register/login/refresh (AuthTokensResponse). */
@Serializable
data class AuthTokens(
	val accessToken: String,
	val refreshToken: String,
	val tokenType: String,
	val expiresInSeconds: Long,
)
