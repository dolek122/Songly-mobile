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
import com.songly.android.pageJson
import com.songly.android.postJson
import com.songly.android.post.PostListViewModel
import com.songly.android.profileJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Posty w społeczności. Do tej pory ekran społeczności pokazywał tylko opis i
 * członków — treści, dla których społeczność istnieje, nie dało się ani
 * przeczytać, ani napisać.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CommunityPostsTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private val base = "/api/v1/communities/c1"

	private fun backend(myRole: String? = "MEMBER", membership: String = """{"status":"MEMBER","role":"MEMBER"}""") =
		FakeBackend().apply {
			onJson(base, communityJson(myRole = myRole))
			onJson("$base/members", emptyPage)
			onJson("$base/membership", membership)
			onJson("/api/v1/profiles/me", profileJson())
			on(HttpMethod.Get, "$base/posts") {
				pageJson(postJson("p1", "Kto był na koncercie?", authorId = "u2", authorName = "Ada"), number = 0, totalPages = 1)
			}
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
			)
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun showsCommunityPosts() {
		show(backend())

		awaitText("Kto był na koncercie?")
		compose.onNodeWithText("Kto był na koncercie?").assertIsDisplayed()
	}

	@Test
	fun memberPublishesPostThatAppearsOnTop() {
		val backend = backend().apply {
			on(HttpMethod.Post, "$base/posts", HttpStatusCode.Created) { postJson("p2", "Polecam nową płytę") }
		}
		show(backend)
		awaitText("Kto był na koncercie?")

		compose.onNodeWithText("Napisz post w społeczności").performTextInput("Polecam nową płytę")
		compose.onNodeWithText("Opublikuj").performClick()

		awaitText("Polecam nową płytę")
		assertEquals(1, backend.callCount(HttpMethod.Post, "$base/posts"))
	}

	/** Nie-członek czyta (społeczności są publiczne), ale pola publikacji nie dostaje. */
	@Test
	fun nonMemberReadsButCannotWrite() {
		show(backend(myRole = null, membership = """{"status":"NONE"}"""))

		awaitText("Kto był na koncercie?")
		compose.onNodeWithText("Napisz post w społeczności").assertDoesNotExist()
	}

	/** Zbanowany widzi powód i termin — jedyny kanał, którym backend je wydaje (ADR 0015). */
	@Test
	fun bannedUserSeesReasonInsteadOfComposer() {
		show(
			backend(
				myRole = null,
				membership = """{"status":"BANNED","ban":{"reason":"Spam w komentarzach",""" +
					""""createdAt":"2026-01-01T10:00:00Z","expiresAt":null}}""",
			),
		)

		awaitText("bezterminowy zakaz")
		compose.onNodeWithText("Powód: Spam w komentarzach").assertIsDisplayed()
		compose.onNodeWithText("Napisz post w społeczności").assertDoesNotExist()
		// Ban odcina pisanie, nie czytanie.
		compose.onNodeWithText("Kto był na koncercie?").assertIsDisplayed()
	}
}
