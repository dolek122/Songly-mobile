package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.RefreshRequest
import com.songly.shared.session.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Silnik HTTP jest platform-specific (OkHttp na Androidzie, Darwin na iOS),
 * dlatego dostarczany przez `expect`/`actual`. Wspólna konfiguracja (JSON,
 * logowanie, uwierzytelnianie) siedzi w [createHttpClient], żeby obie platformy
 * zachowywały się tak samo.
 */
internal expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>

/**
 * Buduje klienta z wpiętą wtyczką Auth (bearer):
 *  - access token dokładany do żądań z [storage],
 *  - po odpowiedzi 401 Ktor sam woła `/auth/refresh` refresh tokenem, zapisuje
 *    nowe tokeny i ponawia oryginalne żądanie — bez ręcznej pętli w kodzie ekranów.
 *  - gdy refresh się nie powiedzie (refresh token wygasł/unieważniony), czyścimy
 *    [storage]; warstwa wyżej potraktuje to jako wylogowanie.
 */
internal fun createHttpClient(config: ApiConfig, storage: TokenStorage, engine: HttpClientEngine? = null): HttpClient {
	val base = config.baseUrl.trimEnd('/')
	val configure: HttpClientConfig<*>.() -> Unit = {
		expectSuccess = false
		install(ContentNegotiation) {
			json(
				Json {
					ignoreUnknownKeys = true
					isLenient = true
				},
			)
		}
		// Strumień presence (ADR 0007) — wtyczka SSE parsuje ramki i komentarze
		// (heartbeat backendu) za nas, na obu silnikach: OkHttp i Darwin.
		install(SSE)
		// Bez tej wtyczki nie da się ustawić timeoutów per żądanie, a strumień presence
		// potrzebuje dłuższego okna niż zwykłe REST-y (cisza między heartbeatami).
		//
		// Domyślne okno odczytu OkHttp (10 s) jest krótsze niż przebudzenie
		// uśpionego backendu na Renderze (30–60 s): proxy przyjmuje połączenie od
		// razu i trzyma żądanie, aż instancja wstanie. Z domyślnym oknem pierwsze
		// żądanie po kwadransie ciszy kończyło się „Brak połączenia", choć serwer
		// odpowiedziałby kilkadziesiąt sekund później. Połączenie zostaje krótkie —
		// martwy adres ma się ujawnić szybko.
		install(HttpTimeout) {
			connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
			socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
		}
		if (config.enableNetworkLogging) {
			install(Logging) {
				// SIMPLE = println, czyli Logcat (System.out) i konsola Xcode. DEFAULT
				// pisze przez SLF4J, którego na Androidzie nie ma — logowanie było
				// włączone, ale nie wypisywało niczego.
				logger = Logger.SIMPLE
				level = LogLevel.INFO
			}
		}
		install(Auth) {
			bearer {
				loadTokens {
					storage.getTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
				}
				refreshTokens {
					val refreshToken = oldTokens?.refreshToken ?: storage.getTokens()?.refreshToken
					if (refreshToken == null) {
						storage.clear()
						return@refreshTokens null
					}
					val response = client.post("$base/api/v1/auth/refresh") {
						markAsRefreshTokenRequest()
						contentType(ContentType.Application.Json)
						setBody(RefreshRequest(refreshToken))
					}
					if (!response.status.isSuccess()) {
						storage.clear()
						return@refreshTokens null
					}
					val refreshed: AuthTokens = response.body()
					storage.saveTokens(refreshed)
					BearerTokens(refreshed.accessToken, refreshed.refreshToken)
				}
				// Ten klient rozmawia wyłącznie z backendem Songly, więc dokładamy
				// token od razu (bez czekania na challenge 401) — oszczędza rundę.
				sendWithoutRequest { true }
			}
		}
	}
	// Silnik podany z zewnątrz to seam dla testów (MockEngine): pozwala przejść
	// całą realną ścieżkę klienta — serializację, wtyczkę Auth, mapowanie błędów —
	// bez sieci. Produkcyjnie zawsze null, czyli silnik platformy.
	return if (engine != null) HttpClient(engine, configure) else HttpClient(httpClientEngineFactory(), configure)
}

internal const val CONNECT_TIMEOUT_MILLIS = 15_000L

/** Z zapasem ponad najdłuższe obserwowane przebudzenie stagingu (~60 s). */
internal const val SOCKET_TIMEOUT_MILLIS = 90_000L
