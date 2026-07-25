package com.songly.android.feed

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.pageJson
import com.songly.android.postJson
import com.songly.android.profileJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Feed z gestem odświeżania. Sam gest jest tu istotny: pull-to-refresh dodaliśmy
 * niedawno i do tej pory nic poza kompilacją nie potwierdzało, że ściągnięcie
 * listy w dół faktycznie wysyła żądanie i podmienia zawartość.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FeedScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private fun backendWithChangingFeed() = FakeBackend().apply {
		on("/api/v1/feed") { call ->
			val content = if (call == 0) "Stary post" else "Świeży post"
			pageJson(postJson("p$call", content), number = 0, totalPages = 1, totalElements = 1)
		}
		onJson("/api/v1/notifications/unread-count", """{"count":0}""")
		onJson("/api/v1/profiles/me", profileJson())
	}

	private fun show(backend: FakeBackend) {
		val client = backend.client()
		compose.setContent {
			FeedScreen(
				viewModel = FeedViewModel(client),
				client = client,
				onLoggedOut = {},
				onSessionExpired = {},
				onCreatePost = {},
				onOpenComments = {},
				onOpenUserProfile = {},
				onOpenProfile = {},
				onOpenFriends = {},
				onOpenNowPlaying = {},
				onOpenFriendsPresence = {},
				onOpenNotifications = {},
				onOpenCommunities = {},
				onOpenMatches = {},
			)
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun pullToRefreshFetchesFeedAgain() {
		val backend = backendWithChangingFeed()
		show(backend)
		awaitText("Stary post")

		compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }

		awaitText("Świeży post")
		// Dwa żądania: wejście na ekran i gest. Gdyby gest tylko przewijał listę,
		// licznik zostałby na jednym.
		assert(backend.callCount("/api/v1/feed") >= 2) {
			"oczekiwano ponownego żądania feedu po geście, było ${backend.callCount("/api/v1/feed")}"
		}
	}
}
