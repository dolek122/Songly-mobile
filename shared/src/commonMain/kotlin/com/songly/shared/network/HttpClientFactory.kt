package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Silnik HTTP jest platform-specific (OkHttp na Androidzie, Darwin na iOS),
 * dlatego dostarczany przez `expect`/`actual`. Wspólna konfiguracja (JSON,
 * logowanie) siedzi w [createHttpClient], żeby obie platformy zachowywały się
 * tak samo.
 */
internal expect fun httpClientEngineFactory(): io.ktor.client.engine.HttpClientEngineFactory<*>

internal fun createHttpClient(config: ApiConfig): HttpClient =
	HttpClient(httpClientEngineFactory()) {
		expectSuccess = false
		install(ContentNegotiation) {
			json(
				Json {
					ignoreUnknownKeys = true
					isLenient = true
				},
			)
		}
		if (config.enableNetworkLogging) {
			install(Logging) {
				level = LogLevel.INFO
			}
		}
	}
