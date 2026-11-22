package com.songly.android.friends

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.emptyPage
import com.songly.android.friendJson
import com.songly.android.pageJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class FriendsScreenTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private fun twoPagesOfFriends() = FakeBackend().apply {
		on("/api/v1/friends") { call ->
			if (call == 0) {
				pageJson(friendJson("1", "Ada"), number = 0, totalPages = 2, totalElements = 2)
			} else {
				pageJson(friendJson("2", "Bartek"), number = 1, totalPages = 2, totalElements = 2)
			}
		}
		onJson("/api/v1/friends/requests", emptyPage)
		onJson("/api/v1/blocks", emptyPage)
	}

	private fun show(backend: FakeBackend) {
		compose.setContent {
			FriendsScreen(FriendsViewModel(backend.client()), onBack = {}, onOpenUserProfile = {})
		}
	}

	private fun awaitText(text: String) {
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun showsFirstPageOfFriends() {
		show(twoPagesOfFriends())

		awaitText("Ada")
		compose.onNodeWithText("Ada", substring = true).assertIsDisplayed()
	}

	@Test
	fun loadMoreAppendsSecondPage() {
		show(twoPagesOfFriends())
		awaitText("Ada")

		compose.onNodeWithText("Pokaż więcej").performClick()

		awaitText("Bartek")
		compose.onNodeWithText("Ada", substring = true).assertIsDisplayed()
	}

	@Test
	fun loadMoreDisappearsAtEndOfList() {
		val backend = FakeBackend().apply {
			onJson("/api/v1/friends", pageJson(friendJson("1", "Ada"), number = 0, totalPages = 1, totalElements = 1))
			onJson("/api/v1/friends/requests", emptyPage)
			onJson("/api/v1/blocks", emptyPage)
		}
		show(backend)

		awaitText("Ada")
		compose.onAllNodesWithText("Pokaż więcej").assertCountEquals(0)
	}
}
