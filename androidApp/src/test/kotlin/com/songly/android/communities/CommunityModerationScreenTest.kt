package com.songly.android.communities

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.communityJson
import com.songly.android.emptyPage
import com.songly.android.moderation.CommunityModerationViewModel
import com.songly.android.moderation.ReportPostViewModel
import com.songly.android.pageJson
import com.songly.android.post.PostListViewModel
import com.songly.android.postJson
import com.songly.android.profileJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CommunityModerationScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private val base = "/api/v1/communities/c1"

	// Post w społeczności niesie communityId — bez niego nie ma „Zgłoś" (zwykłych postów nie ma kto rozpatrzyć).
	private val communityPost =
		postJson("p1", "Post Bartka", authorId = "u2", authorName = "Bartek").dropLast(1) + ""","communityId":"c1"}"""

	private fun memberJson(userId: String, name: String, role: String) =
		"""{"userId":"$userId","displayName":"$name","avatarUrl":null,"role":"$role","joinedAt":"2026-01-01T10:00:00Z"}"""

	private fun backend(myRole: String?, membership: String) = FakeBackend().apply {
		onJson(base, communityJson(myRole = myRole))
		onJson(
			"$base/members",
			pageJson("${memberJson("owner-1", "Właścicielka", "OWNER")},${memberJson("u2", "Bartek", "MEMBER")}", 0, 1),
		)
		onJson("$base/membership", membership)
		onJson("/api/v1/profiles/me", profileJson())
		on(HttpMethod.Get, "$base/posts") { pageJson(communityPost, 0, 1) }
		onJson("$base/post-reports", emptyPage)
	}

	private fun show(backend: FakeBackend) {
		val client = backend.client()
		compose.setContent {
			CommunityDetailScreen(
				viewModel = CommunityDetailViewModel(client, "c1"),
				postsViewModel = PostListViewModel(client) { page, size -> communityPosts("c1", page, size) },
				onOpenComments = {},
				onBack = {},
				onDeleted = {},
				onOpenUserProfile = {},
				moderationViewModel = CommunityModerationViewModel(client, "c1"),
				createReportViewModel = { ReportPostViewModel(client, it) },
			)
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun regularMemberSeesNoModerationTools() {
		show(backend(myRole = "MEMBER", membership = """{"status":"MEMBER","role":"MEMBER"}"""))
		awaitText("Post Bartka")

		compose.onNodeWithText("Moderacja").assertDoesNotExist()
		// Cudzy post w społeczności da się zgłosić, ale nie usunąć.
		compose.onNodeWithText("Zgłoś").assertIsDisplayed()
		compose.onNodeWithText("Usuń").assertDoesNotExist()
	}

	@Test
	fun moderatorCanDeleteAnyPostAndOpenModeration() {
		show(backend(myRole = "MODERATOR", membership = """{"status":"MEMBER","role":"MODERATOR"}"""))
		awaitText("Post Bartka")

		compose.onNodeWithText("Usuń").assertIsDisplayed()
		compose.onNodeWithText("Moderacja").performClick()

		awaitText("Brak zgłoszeń do rozpatrzenia.")
	}

	/** Role rozdaje wyłącznie właściciel (ADR 0011); właściciela nie da się zbanować. */
	@Test
	fun ownerManagesRolesButCannotBanOwner() {
		show(backend(myRole = "OWNER", membership = """{"status":"MEMBER","role":"OWNER"}"""))
		awaitText("Post Bartka")

		compose.onNodeWithText("Członkowie", substring = true).performClick()

		awaitText("Nadaj moderatora")
		assertEquals(1, compose.onAllNodesWithText("Nadaj moderatora").fetchSemanticsNodes().size)
		assertEquals(1, compose.onAllNodesWithText("Zbanuj").fetchSemanticsNodes().size)
	}

	@Test
	fun bannedUserAppealsAndSeesPendingStatus() {
		val backend = backend(
			myRole = null,
			membership = """{"status":"BANNED","ban":{"reason":"spam","createdAt":"2026-01-01T10:00:00Z"}}""",
		).apply {
			on("$base/membership") { call ->
				if (call == 0) {
					"""{"status":"BANNED","ban":{"reason":"spam","createdAt":"2026-01-01T10:00:00Z"}}"""
				} else {
					"""{"status":"BANNED","ban":{"reason":"spam","createdAt":"2026-01-01T10:00:00Z","appealStatus":"PENDING"}}"""
				}
			}
			on("$base/ban-appeal", HttpStatusCode.NoContent) { "" }
		}
		show(backend)
		awaitText("Powód: spam")

		compose.onNodeWithText("Odwołanie do moderacji").performTextInput("To było nieporozumienie")
		compose.onNodeWithText("Odwołaj się").performClick()

		awaitText("Odwołanie czeka na rozpatrzenie")
		compose.onNodeWithText("Odwołaj się").assertDoesNotExist()
	}

	@Test
	fun rejectedAppealOffersEscalation() {
		show(
			backend(
				myRole = null,
				membership = """{"status":"BANNED","ban":{"reason":"spam","createdAt":"2026-01-01T10:00:00Z",""" +
					""""appealStatus":"REJECTED"}}""",
			),
		)

		awaitText("Odwołanie odrzucone przez moderację")
		compose.onNodeWithText("Przekaż administracji").assertIsDisplayed()
	}
}
