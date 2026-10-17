package com.songly.android.moderation

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.pageJson
import com.songly.android.postJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModerationViewModelsTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private val base = "/api/v1/communities/c1"

	private fun awaitUntil(condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + 5_000
		while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
	}

	private fun error(code: String, status: String = "x") =
		"""{"code":"$code","message":"$status","timestamp":"2026-01-01T00:00:00Z","path":"/p"}"""

	private fun reportJson(id: String, postId: String) =
		"""{"id":"$id","post":${postJson(postId, "treść")},"reporterId":"u9","reporterDisplayName":"Ola",""" +
			""""reason":"SPAM","details":null,"createdAt":"2026-01-01T10:00:00Z","reportCount":2,""" +
			""""reporterUpheldCount":3,"reporterDismissedCount":1}"""

	private fun banJson(userId: String, appealStatus: String?) =
		"""{"userId":"$userId","displayName":"Troll","reason":"spam","createdAt":"2026-01-01T10:00:00Z",""" +
			""""expiresAt":null,"appealStatus":${appealStatus?.let { "\"$it\"" } ?: "null"},"appealText":"przepraszam"}"""

	// --- Zgłoszenie ---

	@Test
	fun reportSendsReasonAndDetails() {
		val backend = FakeBackend().apply { on("/api/v1/posts/p1/reports", HttpStatusCode.Accepted) { "" } }
		val viewModel = ReportPostViewModel(backend.client(), "p1")

		viewModel.selectReason("HARASSMENT")
		viewModel.onDetailsChange("  obraża ludzi  ")
		viewModel.submit()
		awaitUntil { viewModel.uiState.submitted }

		val body = backend.lastBody(HttpMethod.Post, "/api/v1/posts/p1/reports")!!
		assertTrue(body, body.contains("\"reason\":\"HARASSMENT\"") && body.contains("\"details\":\"obraża ludzi\""))
	}

	/** Bez powodu nie ma czego wysłać — backend wymaga go (@NotNull). */
	@Test
	fun reportRequiresReason() {
		val viewModel = ReportPostViewModel(FakeBackend().client(), "p1")
		assertEquals(false, viewModel.uiState.canSubmit)
	}

	@Test
	fun rateLimitedReportExplainsWithoutNumbers() {
		val backend = FakeBackend().apply {
			on("/api/v1/posts/p1/reports", HttpStatusCode.TooManyRequests) { error("REPORT_RATE_LIMIT_EXCEEDED") }
		}
		val viewModel = ReportPostViewModel(backend.client(), "p1")

		viewModel.selectReason("SPAM")
		viewModel.submit()
		awaitUntil { !viewModel.uiState.isSubmitting && viewModel.uiState.errorMessage != null }

		assertEquals("Zgłaszasz za często. Spróbuj ponownie później.", viewModel.uiState.errorMessage)
	}

	// --- Kolejka zgłoszeń ---

	/**
	 * Usunięcie posta zabiera na backendzie wszystkie jego zgłoszenia (kaskada),
	 * więc kolejka nie może dalej pokazywać pozostałych zgłoszeń tego posta.
	 */
	@Test
	fun removingPostClearsAllItsReports() {
		val backend = FakeBackend().apply {
			onJson("$base/post-reports", pageJson("${reportJson("r1", "p1")},${reportJson("r2", "p1")},${reportJson("r3", "p2")}", 0, 1))
			on(HttpMethod.Delete, "/api/v1/posts/p1", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = CommunityModerationViewModel(backend.client(), "c1")
		viewModel.select(ModerationSection.REPORTS)
		awaitUntil { viewModel.uiState.reports.items.size == 3 }

		viewModel.removePost(viewModel.uiState.reports.items.first())
		awaitUntil { viewModel.uiState.busyId == null && viewModel.uiState.statusMessage != null }

		assertEquals(listOf("r3"), viewModel.uiState.reports.items.map { it.id })
		assertEquals(1, backend.callCount(HttpMethod.Delete, "/api/v1/posts/p1"))
	}

	@Test
	fun dismissingReportKeepsOtherReportsOfSamePost() {
		val backend = FakeBackend().apply {
			onJson("$base/post-reports", pageJson("${reportJson("r1", "p1")},${reportJson("r2", "p1")}", 0, 1))
			on("$base/post-reports/r1/dismissal", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = CommunityModerationViewModel(backend.client(), "c1")
		viewModel.select(ModerationSection.REPORTS)
		awaitUntil { viewModel.uiState.reports.items.size == 2 }

		viewModel.dismiss(viewModel.uiState.reports.items.first())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals(listOf("r2"), viewModel.uiState.reports.items.map { it.id })
	}

	// --- Bany i odwołania ---

	/** Zdjęcie bana z oczekującym odwołaniem JEST jego uznaniem (ADR 0017) — i tak to nazywamy. */
	@Test
	fun unbanWithPendingAppealIsReportedAsAcceptedAppeal() {
		val backend = FakeBackend().apply {
			onJson("$base/bans", pageJson(banJson("u1", "PENDING"), 0, 1))
			on(HttpMethod.Delete, "$base/bans/u1", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = CommunityModerationViewModel(backend.client(), "c1")
		viewModel.select(ModerationSection.BANS)
		awaitUntil { viewModel.uiState.bans.items.isNotEmpty() }

		viewModel.unban(viewModel.uiState.bans.items.first())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals("Uznano odwołanie", viewModel.uiState.statusMessage)
		assertTrue(viewModel.uiState.bans.items.isEmpty())
	}

	@Test
	fun rejectedAppealStaysOnListWithNewStatus() {
		val backend = FakeBackend().apply {
			onJson("$base/bans", pageJson(banJson("u1", "PENDING"), 0, 1))
			on("$base/bans/u1/appeal/rejection", HttpStatusCode.NoContent) { "" }
		}
		val viewModel = CommunityModerationViewModel(backend.client(), "c1")
		viewModel.select(ModerationSection.BANS)
		awaitUntil { viewModel.uiState.bans.items.isNotEmpty() }

		viewModel.rejectAppeal(viewModel.uiState.bans.items.first())
		awaitUntil { viewModel.uiState.statusMessage != null }

		assertEquals("REJECTED", viewModel.uiState.bans.items.single().appealStatus)
	}
}
