package com.songly.shared.model

import kotlinx.serialization.Serializable

/**
 * Modele uwierzytelniania — odwzorowują DTO z backendu (moduł `identity`).
 * Źródłem prawdy jest kontrakt OpenAPI (`docs/api/openapi.json` w Songly-backend);
 * przy zmianie pól po stronie serwera zaktualizuj te klasy.
 */
@Serializable
data class RegisterRequest(val email: String, val password: String)

/** Potwierdzenie adresu e-mail tokenem z linku (backend: identity.dto.VerifyEmailRequest). */
@Serializable
data class VerifyEmailRequest(val token: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/**
 * Potwierdzenie usunięcia konta (backend: identity.dto.DeleteAccountRequest).
 * Ponowne hasło jest wymagane — sam ważny access token nie wystarcza na akcję
 * nieodwracalną (Claude.md §9, hard delete + RODO).
 */
@Serializable
data class DeleteAccountRequest(val password: String)

/** Odpowiedź backendu na register/login/refresh (AuthTokensResponse). */
@Serializable
data class AuthTokens(
	val accessToken: String,
	val refreshToken: String,
	val tokenType: String,
	val expiresInSeconds: Long,
)
