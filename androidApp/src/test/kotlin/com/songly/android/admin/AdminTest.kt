package com.songly.android.admin

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.account.SuspendedViewModel
import com.songly.android.feed.FeedViewModel
import com.songly.android.profile.UserProfileViewModel
import com.songly.android.profileJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private fun awaitUntil(condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + 5_000
		while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
	}

	private fun error(code: String) = """{"code":"$code","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"/p"}"""

	private fun suspensionJson(userId: String, appeal: String?) =
		"""{"userId":"$userId","reason":"nękanie","suspendedAt":"2026-01-01T10:00:00Z","expiresAt":null,""" +
			""""appealStatus":${appeal?.let { "\"$it\"" } ?: "null"},"appealText":"to pomyłka"}"""

	// --- Wykrywanie roli ---

	@Test
	fun adminIsRecognizedByOwnIdOnAdminList() {
		val client = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			onJson("/api/v1/admin/admins", """{"userIds":["me-1","other"]}""")
		}.client()

		assertTrue(runBlocking { client.isCurrentUserAdmin() })
	}

	/** Zwykły user dostaje 403 — to odpowiedź „nie jesteś adminem", nie awaria. */
	@Test
	fun forbiddenAdminListMeansNotAdmin() {
		val client = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			on("/api/v1/admin/admins", HttpStatusCode.Forbidden) { error("ACCESS_DENIED") }
		}.client()

		assertFalse(runBlocking { client.isCurrentUserAdmin() })
	}

	// --- Zawieszenia ---

	@Test
	fun liftingWithPendingAppealIsReportedAsAcceptedAppeal() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			onJson("/api/v1/admin/suspensions", "[${suspensionJson("u1", "PENDING")}]")
			on(HttpMethod.Delete, "/api/v1/admin/users/u1/suspension", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = AdminViewModel(backend.client())
		viewModel.select(AdminSection.SUSPENSIONS)
		awaitUntil { viewModel.uiState.suspensions.isNotEmpty() }

		viewModel.lift(viewModel.uiState.suspensions.single())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals("Uznano odwołanie, zawieszenie zdjęte", viewModel.uiState.statusMessage)
		assertTrue(viewModel.uiState.suspensions.isEmpty())
	}

	@Test
	fun rejectedSuspensionAppealStaysWithNewStatus() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			onJson("/api/v1/admin/suspensions", "[${suspensionJson("u1", "PENDING")}]")
			on("/api/v1/admin/users/u1/suspension/appeal/rejection", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = AdminViewModel(backend.client())
		viewModel.select(AdminSection.SUSPENSIONS)
		awaitUntil { viewModel.uiState.suspensions.isNotEmpty() }

		viewModel.rejectAppeal(viewModel.uiState.suspensions.single())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals("REJECTED", viewModel.uiState.suspensions.single().appealStatus)
	}

	/**
	 * Profil zawieszonego jest ukryty (404, ADR 0026), więc nazwa musi przyjść z
	 * endpointu administracji (ADR 0036) — inaczej wykaz zawieszeń pokazywałby same id.
	 */
	@Test
	fun suspendedAccountsGetNamesFromAdminEndpoint() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			onJson("/api/v1/admin/suspensions", "[${suspensionJson("u1", null)}]")
			on("/api/v1/profiles/u1", HttpStatusCode.NotFound) { error("PROFILE_NOT_FOUND") }
			onJson("/api/v1/admin/profiles", """[{"userId":"u1","displayName":"Troll","suspended":true}]""")
		}
		val viewModel = AdminViewModel(backend.client())

		viewModel.select(AdminSection.SUSPENSIONS)
		awaitUntil { !viewModel.uiState.isLoading && viewModel.uiState.suspensions.isNotEmpty() }

		assertEquals("Troll", viewModel.uiState.names["u1"])
		assertEquals(1, backend.callCount("/api/v1/admin/profiles"))
	}

	// --- Eskalacje ---

	/** Wykaz niesie same id — nazwy dociągamy z profili, i nie mogą zginąć przy zapisie stanu. */
	@Test
	fun escalationsResolveNamesAndOverturnRemovesEntry() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/profiles/me", profileJson("me-1"))
			onJson(
				"/api/v1/admin/ban-appeals",
				"""[{"userId":"u1","communityId":"c1","communityName":"Jazz","reason":"spam",""" +
					""""bannedAt":"2026-01-01T10:00:00Z","appealText":"nie spamowałem"}]""",
			)
			onJson("/api/v1/admin/profiles", """[{"userId":"u1","displayName":"Bartek","suspended":false}]""")
			on(HttpMethod.Delete, "/api/v1/admin/communities/c1/bans/u1", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = AdminViewModel(backend.client())
		viewModel.select(AdminSection.ESCALATIONS)
		awaitUntil { !viewModel.uiState.isLoading && viewModel.uiState.escalations.isNotEmpty() }

		assertEquals("Bartek", viewModel.uiState.names["u1"])

		viewModel.overturn(viewModel.uiState.escalations.single())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals("Uchylono ban", viewModel.uiState.statusMessage)
		assertTrue(viewModel.uiState.escalations.isEmpty())
	}

	// --- Zawieszenie z profilu ---

	@Test
	fun suspendRequiresReasonAndSendsIt() {
		val backend = FakeBackend().apply {
			on(HttpMethod.Put, "/api/v1/admin/users/u2/suspension", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = UserProfileViewModel(backend.client(), "u2")
		viewModel.openSuspendDialog()
		assertFalse("powód jest obowiązkowy", viewModel.uiState.canSuspend)

		viewModel.onSuspendReasonChange("nękanie innych")
		viewModel.onSuspendDaysChange(null)
		viewModel.suspend()
		awaitUntil { viewModel.uiState.adminMessage != null || viewModel.uiState.suspendError != null }

		assertEquals("Konto zawieszone", viewModel.uiState.adminMessage)
		val body = backend.lastBody(HttpMethod.Put, "/api/v1/admin/users/u2/suspension")!!
		assertTrue(body, body.contains("\"reason\":\"nękanie innych\"") && !body.contains("durationDays"))
	}

	// --- Zawieszone konto ---

	@Test
	fun suspendedUserAppealsOnce() {
		val backend = FakeBackend().apply {
			on("/api/v1/auth/me/suspension") { call ->
				if (call == 0) {
					"""{"suspended":true,"reason":"nękanie","suspendedAt":"2026-01-01T10:00:00Z"}"""
				} else {
					"""{"suspended":true,"reason":"nękanie","suspendedAt":"2026-01-01T10:00:00Z","appealStatus":"PENDING"}"""
				}
			}
			on("/api/v1/auth/me/suspension/appeal", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = SuspendedViewModel(backend.client())
		viewModel.load()
		awaitUntil { viewModel.uiState.suspension != null }
		assertEquals("nękanie", viewModel.uiState.suspension?.reason)

		viewModel.onAppealTextChange("To było nieporozumienie")
		viewModel.submitAppeal()
		awaitUntil { viewModel.uiState.suspension?.appealStatus == "PENDING" }

		assertEquals("PENDING", viewModel.uiState.suspension?.appealStatus)
	}

	/** Zawieszenie wygasło (leniwie, ADR 0022) — ekran ma oddać aplikację, nie wisieć. */
	@Test
	fun expiredSuspensionReturnsToApp() {
		val backend = FakeBackend().apply { onJson("/api/v1/auth/me/suspension", """{"suspended":false}""") }
		val viewModel = SuspendedViewModel(backend.client())

		viewModel.load()
		awaitUntil { !viewModel.uiState.isLoading }

		assertTrue(viewModel.uiState.lifted)
	}

	/** Zawieszenie w trakcie sesji: feed nie pokazuje gołego błędu, tylko przekazuje sprawę dalej. */
	@Test
	fun feedRecognizesSuspendedAccount() {
		val backend = FakeBackend().apply {
			on("/api/v1/feed", HttpStatusCode.Forbidden) { error("ACCOUNT_SUSPENDED") }
			on("/api/v1/notifications/unread-count", HttpStatusCode.Forbidden) { error("ACCOUNT_SUSPENDED") }
			on("/api/v1/profiles/me", HttpStatusCode.Forbidden) { error("ACCOUNT_SUSPENDED") }
		}
		val viewModel = FeedViewModel(backend.client())

		viewModel.refresh()
		awaitUntil { viewModel.uiState.accountSuspended || viewModel.uiState.errorMessage != null }

		assertTrue(viewModel.uiState.accountSuspended)
	}
}
