package com.songly.android

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.AuthTokens
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.session.InMemoryTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking

/**
 * Atrapa backendu dla testów UI: podstawiony silnik Ktora zamiast sieci.
 *
 * Podstawiamy warstwę **najniżej jak się da** — pod klientem, nie zamiast niego.
 * Dzięki temu test przechodzi realną ścieżkę: serializację JSON, wtyczkę Auth,
 * mapowanie błędów i logikę ViewModelu. Atrapa ViewModelu sprawdzałaby wyłącznie
 * to, że atrapa zwraca to, co jej kazano.
 */
class FakeBackend {

	private val handlers = mutableMapOf<String, (callIndex: Int) -> Reply>()
	private val callCounts = mutableMapOf<String, Int>()

	data class Reply(val status: HttpStatusCode, val body: String)

	/**
	 * Odpowiedź na dokładną ścieżkę (bez query). [body] dostaje numer wywołania od
	 * zera, więc kolejne żądania o tę samą ścieżkę mogą oddać różne strony — bez
	 * tego nie da się przetestować doczytywania.
	 */
	fun on(path: String, status: HttpStatusCode = HttpStatusCode.OK, body: (callIndex: Int) -> String) {
		handlers[path] = { index -> Reply(status, body(index)) }
	}

	fun onJson(path: String, body: String) = on(path) { body }

	/**
	 * Odpowiedź tylko dla jednej metody — gdy ta sama ścieżka znaczy dwie operacje
	 * (np. `GET` listy i `POST` publikacji w społeczności). Ma pierwszeństwo przed
	 * [on] dla tej ścieżki.
	 */
	fun on(method: HttpMethod, path: String, status: HttpStatusCode = HttpStatusCode.OK, body: (callIndex: Int) -> String) {
		handlers["${method.value} $path"] = { index -> Reply(status, body(index)) }
	}

	/** Ile razy odpytano ścieżkę — do sprawdzenia, że odświeżenie faktycznie poszło. */
	fun callCount(path: String): Int = callCounts[path] ?: 0

	fun callCount(method: HttpMethod, path: String): Int = callCounts["${method.value} $path"] ?: 0

	private val lastBodies = mutableMapOf<String, String>()

	/** Ostatnie ciało JSON wysłane danym żądaniem — do sprawdzenia, co klient wysłał. */
	fun lastBody(method: HttpMethod, path: String): String? = lastBodies["${method.value} $path"]

	fun client(): SonglyApiClient {
		val engine = MockEngine { request ->
			val path = request.url.encodedPath
			val methodKey = "${request.method.value} $path"
			(request.body as? TextContent)?.let { lastBodies[methodKey] = it.text }
			for (key in listOf(path, methodKey)) {
				callCounts[key] = callCounts.getOrElse(key) { 0 } + 1
			}
			val methodHandler = handlers[methodKey]
			val index = (if (methodHandler != null) callCounts[methodKey] else callCounts[path])!! - 1
			val handler = methodHandler ?: handlers[path]
			if (handler == null) {
				respondError(HttpStatusCode.NotFound, """{"code":"NOT_FOUND","message":"brak atrapy dla $path"}""")
			} else {
				val reply = handler(index)
				respond(
					content = reply.body,
					status = reply.status,
					headers = headersOf(HttpHeaders.ContentType, "application/json"),
				)
			}
		}
		val storage = InMemoryTokenStorage()
		// Sesja założona z góry: testy ekranów nie przechodzą przez logowanie, a bez
		// tokenów wtyczka Auth próbowałaby odświeżania przy pierwszym żądaniu.
		runBlocking { storage.saveTokens(AuthTokens("access", "refresh", "Bearer", 900)) }
		return SonglyApiClient.withEngine(ApiConfig("http://test.local"), storage, engine)
	}
}

/** Strona `PagedModel` backendu z gotowymi metadanymi. */
fun pageJson(content: String, number: Int, totalPages: Int, totalElements: Int = 0): String =
	"""{"content":[$content],"page":{"size":20,"number":$number,"totalElements":$totalElements,"totalPages":$totalPages}}"""

val emptyPage: String get() = pageJson("", number = 0, totalPages = 0)

fun friendJson(userId: String, name: String): String =
	"""{"userId":"$userId","displayName":"$name","avatarUrl":null,"friendsSince":"2026-01-01T10:00:00Z"}"""

fun profileJson(userId: String = "me-1"): String =
	"""{"userId":"$userId","displayName":"Ja","bio":null,"avatarUrl":null,"createdAt":"2026-01-01T10:00:00Z"}"""

fun postJson(id: String, content: String): String =
	"""{"id":"$id","authorId":"me-1","authorDisplayName":"Ja","authorAvatarUrl":null,"content":"$content",""" +
		""""createdAt":"2026-01-01T10:00:00Z","likeCount":0,"likedByMe":false,"commentCount":0,""" +
		""""shareCount":0,"sharedByMe":false}"""

fun postJson(id: String, content: String, authorId: String, authorName: String): String =
	postJson(id, content).replace(""""authorId":"me-1","authorDisplayName":"Ja"""", """"authorId":"$authorId","authorDisplayName":"$authorName"""")

fun communityJson(id: String = "c1", myRole: String? = "MEMBER"): String =
	"""{"id":"$id","name":"Jazz","description":null,"ownerId":"owner-1","ownerDisplayName":"Właściciel",""" +
		""""ownerAvatarUrl":null,"memberCount":2,"myRole":${myRole?.let { "\"$it\"" } ?: "null"},""" +
		""""createdAt":"2026-01-01T10:00:00Z"}"""
