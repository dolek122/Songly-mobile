package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.ApiError
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.LoginRequest
import com.songly.shared.model.Page
import com.songly.shared.model.Post
import com.songly.shared.model.RefreshRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.SonglyApiException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Jedyny punkt styku mobilki z REST API Songly. Trzyma warstwę sieciową w
 * `shared` (KMP, §14 blueprintu backendu), żeby Android i iOS dzieliły dokładnie
 * tę samą logikę wywołań i mapowania błędów. UI dostaje gotowe modele albo
 * [SonglyApiException] w spójnym formacie [ApiError].
 *
 * Ten klient jest bezstanowy w kwestii tokenów — przechowywaniem i odświeżaniem
 * access/refresh tokenów zajmie się osobna warstwa (repozytorium sesji), którą
 * dołożymy przy pierwszym ekranie logowania.
 */
class SonglyApiClient(private val config: ApiConfig) {

	private val http = createHttpClient(config)
	private val base = config.baseUrl.trimEnd('/')

	suspend fun register(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/register") {
			contentType(ContentType.Application.Json)
			setBody(RegisterRequest(email, password))
		}.decodeOrThrow()

	suspend fun login(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(LoginRequest(email, password))
		}.decodeOrThrow()

	suspend fun refresh(refreshToken: String): AuthTokens =
		http.post("$base/api/v1/auth/refresh") {
			contentType(ContentType.Application.Json)
			setBody(RefreshRequest(refreshToken))
		}.decodeOrThrow()

	suspend fun feed(accessToken: String, page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/feed") {
			header(HttpHeaders.Authorization, "Bearer $accessToken")
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	fun close() = http.close()

	private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
		if (status.isSuccess()) {
			return body()
		}
		val error = runCatching { body<ApiError>() }.getOrNull()
		throw SonglyApiException(
			status = status.value,
			error = error,
			message = error?.let { "${it.code}: ${it.message}" } ?: "HTTP ${status.value}",
		)
	}
}
