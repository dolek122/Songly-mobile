package com.songly.android.push

import com.songly.android.FakeBackend
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rejestracja urządzenia push w kliencie (backend ADR 0040). */
class PushClientTest {

	private val removal = "/api/v1/notifications/push-devices/removal"

	private fun backend() = FakeBackend().apply {
		on(HttpMethod.Put, "/api/v1/notifications/push-devices", HttpStatusCode.NoContent) { "" }
		on(removal, HttpStatusCode.NoContent) { "" }
		on("/api/v1/auth/logout", HttpStatusCode.NoContent) { "" }
	}

	@Test
	fun registrationSendsTokenAndPlatform() {
		val backend = backend()
		val client = backend.client()

		runBlocking { client.registerPushDevice("fcm-token-1", "ANDROID") }

		val body = backend.lastBody(HttpMethod.Put, "/api/v1/notifications/push-devices")!!
		assertTrue(body, body.contains("\"token\":\"fcm-token-1\"") && body.contains("\"platform\":\"ANDROID\""))
	}

	/**
	 * Backend nie wie, które urządzenie należy do której sesji, więc wylogowany
	 * telefon dalej dostawałby pushe, gdyby klient go nie wyrejestrował.
	 */
	@Test
	fun logoutUnregistersTheDeviceFromThisSession() {
		val backend = backend()
		val client = backend.client()
		runBlocking { client.registerPushDevice("fcm-token-2", "ANDROID") }

		runBlocking { client.logout() }

		assertEquals(1, backend.callCount(removal))
		assertTrue(backend.lastBody(HttpMethod.Post, removal)!!.contains("\"token\":\"fcm-token-2\""))
		assertEquals(1, backend.callCount("/api/v1/auth/logout"))
	}

	@Test
	fun logoutWithoutRegisteredDeviceDoesNotCallRemoval() {
		val backend = backend()

		runBlocking { backend.client().logout() }

		assertEquals(0, backend.callCount(removal))
	}

	/** Awaria wyrejestrowania nie może zatrzymać wylogowania. */
	@Test
	fun failedRemovalStillLogsOut() {
		val backend = backend().apply { on(removal, HttpStatusCode.InternalServerError) { "{}" } }
		val client = backend.client()
		runBlocking { client.registerPushDevice("fcm-token-3", "ANDROID") }

		runBlocking { client.logout() }

		assertEquals(1, backend.callCount("/api/v1/auth/logout"))
		assertTrue(!runBlocking { client.isLoggedIn() })
	}
}
