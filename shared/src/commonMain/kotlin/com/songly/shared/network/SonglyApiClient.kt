package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.ApiError
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.Comment
import com.songly.shared.model.CreateCommentRequest
import com.songly.shared.model.CreatePostRequest
import com.songly.shared.model.LoginRequest
import com.songly.shared.model.Page
import com.songly.shared.model.Post
import com.songly.shared.model.RefreshRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.SonglyApiException
import com.songly.shared.session.InMemoryTokenStorage
import com.songly.shared.session.TokenStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Jedyny punkt styku mobilki z REST API Songly. Trzyma warstwę sieciową i sesji
 * w `shared` (KMP, §14 blueprintu backendu), żeby Android i iOS dzieliły dokładnie
 * tę samą logikę wywołań, mapowania błędów i odświeżania tokenów.
 *
 * Sesja: po [register]/[login] tokeny lądują w [storage]; przy każdym żądaniu
 * uwierzytelnionym access token dokłada wtyczka Auth, a po 401 sama odświeża go
 * refresh tokenem (patrz [createHttpClient]). UI nie dotyka tokenów — woła metody
 * domenowe i ewentualnie łapie [SonglyApiException].
 */
class SonglyApiClient(
	config: ApiConfig,
	private val storage: TokenStorage = InMemoryTokenStorage(),
) {

	private val http = createHttpClient(config, storage)
	private val base = config.baseUrl.trimEnd('/')

	/** Czy istnieje zapisana sesja (są tokeny). Nie gwarantuje ich ważności. */
	suspend fun isLoggedIn(): Boolean = storage.getTokens() != null

	suspend fun register(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/register") {
			contentType(ContentType.Application.Json)
			setBody(RegisterRequest(email, password))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	suspend fun login(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(LoginRequest(email, password))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	/**
	 * Kończy sesję: unieważnia refresh token na serwerze (best-effort) i czyści
	 * lokalny stan. Nawet gdy żądanie do serwera padnie, lokalnie wylogowujemy.
	 */
	suspend fun logout() {
		val refreshToken = storage.getTokens()?.refreshToken
		if (refreshToken != null) {
			runCatching {
				http.post("$base/api/v1/auth/logout") {
					contentType(ContentType.Application.Json)
					setBody(RefreshRequest(refreshToken))
				}
			}
		}
		endSession()
	}

	suspend fun feed(page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/feed") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Tworzy nowy post autorstwa zalogowanego użytkownika. */
	suspend fun createPost(content: String): Post =
		http.post("$base/api/v1/posts") {
			contentType(ContentType.Application.Json)
			setBody(CreatePostRequest(content))
		}.decodeOrThrow()

	/** Polubienie posta (idempotentne po stronie serwera). */
	suspend fun likePost(postId: String) =
		http.put("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	/** Cofnięcie polubienia. */
	suspend fun unlikePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	/** Komentarze posta, stronicowane. */
	suspend fun comments(postId: String, page: Int = 0, size: Int = 50): Page<Comment> =
		http.get("$base/api/v1/posts/$postId/comments") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Dodaje komentarz do posta i zwraca go. */
	suspend fun addComment(postId: String, content: String): Comment =
		http.post("$base/api/v1/posts/$postId/comments") {
			contentType(ContentType.Application.Json)
			setBody(CreateCommentRequest(content))
		}.decodeOrThrow()

	fun close() = http.close()

	private suspend fun startSession(tokens: AuthTokens) {
		storage.saveTokens(tokens)
		// Wymuś ponowne wczytanie tokenów przez wtyczkę Auth — inaczej trzymałaby
		// w cache poprzedni (albo brak) token z czasu sprzed logowania.
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend fun endSession() {
		storage.clear()
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
		if (status.isSuccess()) {
			return body()
		}
		throw toApiException()
	}

	/** Dla odpowiedzi bez ciała (204) — tylko weryfikuje status. */
	private suspend fun HttpResponse.ensureSuccessOrThrow() {
		if (!status.isSuccess()) {
			throw toApiException()
		}
	}

	private suspend fun HttpResponse.toApiException(): SonglyApiException {
		val error = runCatching { body<ApiError>() }.getOrNull()
		return SonglyApiException(
			status = status.value,
			error = error,
			message = error?.let { "${it.code}: ${it.message}" } ?: "HTTP ${status.value}",
		)
	}
}
