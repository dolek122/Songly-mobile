package com.songly.shared.config

/**
 * Konfiguracja połączenia z backendem. [baseUrl] wskazuje na środowisko:
 * lokalny dev, staging (Render) albo prod. Ustawiaj per build variant, nie
 * hardkoduj w kodzie ekranów.
 *
 * Uwaga dla Androida: emulator widzi hosta jako `10.0.2.2`, nie `localhost`.
 */
data class ApiConfig(
	val baseUrl: String,
	val enableNetworkLogging: Boolean = false,
) {
	companion object {
		/** Backend uruchomiony lokalnie, widziany z emulatora Androida. */
		val LOCAL_ANDROID_EMULATOR = ApiConfig(
			baseUrl = "http://10.0.2.2:8080",
			enableNetworkLogging = true,
		)

		/** Backend lokalny widziany z symulatora iOS / hosta. */
		val LOCAL_HOST = ApiConfig(
			baseUrl = "http://localhost:8080",
			enableNetworkLogging = true,
		)

		// Staging na Render (free tier — usypia po ~15 min bezczynności, pierwszy
		// request po przebudzeniu trwa ~30–60 s; potwierdzony 2026-07-21).
		val STAGING = ApiConfig(
			baseUrl = "https://songly-backend-q7zm.onrender.com",
			enableNetworkLogging = true,
		)
	}
}
