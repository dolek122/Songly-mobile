package com.songly.android.post

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
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
class PostDetailScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private fun show(backend: FakeBackend, onOpenComments: (String) -> Unit = {}) {
		compose.setContent {
			PostDetailScreen(
				viewModel = PostDetailViewModel(backend.client(), "p1"),
				onBack = {},
				onOpenComments = onOpenComments,
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
	fun showsPostAndLeadsToComments() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/posts/p1", postJson("p1", "Mój post"))
			onJson("/api/v1/profiles/me", profileJson())
		}
		var opened: String? = null
		show(backend) { opened = it }

		awaitText("Mój post")
		compose.onNodeWithText("Pokaż komentarze", substring = true).performClick()

		assertEquals("p1", opened)
	}

	@Test
	fun likeGoesToServer() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/posts/p1", postJson("p1", "Mój post"))
			onJson("/api/v1/profiles/me", profileJson())
			on(HttpMethod.Put, "/api/v1/posts/p1/likes", HttpStatusCode.NoContent) { "" }
		}
		show(backend)
		awaitText("Mój post")

		compose.onNodeWithText("♡ 0").performClick()

		awaitText("♥ 1")
		compose.waitUntil(timeoutMillis = 5_000) { backend.callCount(HttpMethod.Put, "/api/v1/posts/p1/likes") == 1 }
	}

	/**
	 * Post usunięty albo autora, który nas zablokował — backend zwraca to samo 404,
	 * a powiadomienie o nim wciąż leży w skrzynce. Ekran ma to powiedzieć po ludzku.
	 */
	@Test
	fun unavailablePostShowsReadableMessage() {
		val backend = FakeBackend().apply {
			on("/api/v1/posts/p1", HttpStatusCode.NotFound) {
				"""{"code":"POST_NOT_FOUND","message":"x","timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/posts/p1"}"""
			}
		}
		show(backend)

		awaitText("nie jest już dostępny")
		compose.onNodeWithText("Ten post nie jest już dostępny.").assertIsDisplayed()
	}
}
