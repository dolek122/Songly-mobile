package com.songly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class VerifyEmailRequest(val token: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class AuthTokens(
	val accessToken: String,
	val refreshToken: String,
	val tokenType: String,
	val expiresInSeconds: Long,
)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class PasswordResetRequest(val email: String)

@Serializable
data class PasswordResetConfirmRequest(val token: String, val newPassword: String)
