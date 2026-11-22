package com.songly.shared.config

data class ApiConfig(
	val baseUrl: String,
	val enableNetworkLogging: Boolean = false,
) {
	companion object {
		val LOCAL_ANDROID_EMULATOR = ApiConfig(
			baseUrl = "http://10.0.2.2:8080",
			enableNetworkLogging = true,
		)

		val LOCAL_HOST = ApiConfig(
			baseUrl = "http://localhost:8080",
			enableNetworkLogging = true,
		)

		val STAGING = ApiConfig(
			baseUrl = "https://songly-backend-q7zm.onrender.com",
			enableNetworkLogging = true,
		)
	}
}
