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

internal expect fun httpClientEngineFactory(): HttpClientEngineFactory<*>

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
		install(SSE)
		install(HttpTimeout) {
			connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
			socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
		}
		if (config.enableNetworkLogging) {
			install(Logging) {
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
				sendWithoutRequest { true }
			}
		}
	}
	return if (engine != null) HttpClient(engine, configure) else HttpClient(httpClientEngineFactory(), configure)
}

internal const val CONNECT_TIMEOUT_MILLIS = 15_000L

internal const val SOCKET_TIMEOUT_MILLIS = 90_000L
