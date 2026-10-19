package com.songly.android.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.auth.LoginScreen
import com.songly.android.auth.LoginViewModel
import com.songly.android.feed.FeedScreen
import com.songly.android.feed.FeedViewModel
import com.songly.android.notifications.MailPreferencesScreen
import com.songly.android.notifications.MailPreferencesViewModel
import com.songly.android.pageJson
import com.songly.android.postJson
import com.songly.android.profileJson
import com.songly.shared.model.Post
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * To, co dostaje TalkBack, a nie to, co widać. Każdy test odpowiada jednemu
 * problemowi z przeglądu dostępności: bez tych asercji poprawki semantyki nie
 * zostawiały śladu w żadnym innym teście, więc mogły zniknąć niezauważone przy
 * pierwszym refaktorze ekranu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AccessibilityTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

	private fun hasState(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

	private val post = Post(
		id = "p1", authorId = "u2", authorDisplayName = "Ola", content = "Treść", createdAt = "2026-01-01T10:00:00Z",
		likeCount = 3, likedByMe = true, commentCount = 2, shareCount = 1, sharedByMe = false,
	)

	private fun showPostCard() = compose.setContent {
		PostCard(
			post = post, canDelete = true, onToggleLike = {}, onOpenComments = {}, onToggleShare = {},
			onDelete = {}, onOpenAuthor = {}, onReport = {},
		)
	}

	/** „♡ 3" czytane było jako „biały kier, trzy" — bez roli, stanu i z celem ~20 dp. */
	@Test
	fun postCountersAreLabelledButtonsWithStateAndFullTouchTarget() {
		showPostCard()

		compose.onNodeWithContentDescription("Polubienia: 3")
			.assert(hasRole(Role.Button))
			.assert(hasState("polubiony"))
			.assertHeightIsAtLeast(48.dp)
		compose.onNodeWithContentDescription("Udostępnienia: 1").assert(hasState("nieudostępniony"))
		compose.onNodeWithContentDescription("Komentarze: 2").assert(hasRole(Role.Button))
		compose.onNodeWithContentDescription("Usuń post").assert(hasRole(Role.Button)).assertHeightIsAtLeast(48.dp)
		compose.onNodeWithContentDescription("Zgłoś post").assert(hasRole(Role.Button))
	}

	/** Awatar obok nazwy powtarzał ją („Awatar: Ola, Ola"); inicjały czytane były „O L". */
	@Test
	fun avatarDoesNotRepeatTheName() {
		showPostCard()

		assertEquals(0, compose.onAllNodes(hasContentDescription("Awatar", substring = true)).fetchSemanticsNodes().size)
		assertEquals(0, compose.onAllNodesWithText("O").fetchSemanticsNodes().size)
	}

	/** Plakietka z liczbą nieprzeczytanych jest tylko wizualna — dzwonek musi ją powiedzieć słowami. */
	@Test
	fun feedBellAnnouncesUnreadNotifications() {
		val client = FakeBackend().apply {
			onJson("/api/v1/feed", pageJson(postJson("p1", "Post"), number = 0, totalPages = 1))
			onJson("/api/v1/notifications/unread-count", """{"count":3}""")
			onJson("/api/v1/profiles/me", profileJson())
		}.client()
		compose.setContent {
			FeedScreen(
				viewModel = FeedViewModel(client), onSessionExpired = {}, onCreatePost = {}, onOpenComments = {},
				onOpenUserProfile = {}, onOpenNotifications = {},
			)
		}

		compose.waitUntil(5_000) {
			compose.onAllNodes(hasContentDescription("Powiadomienia, nieprzeczytane: 3"))
				.fetchSemanticsNodes().isNotEmpty()
		}
	}

	/**
	 * Przełącznikiem jest cały wiersz — inaczej TalkBack mówił „przełącznik, włączony"
	 * bez etykiety, a palcem trzeba było trafić w sam Switch.
	 */
	@Test
	fun preferenceRowIsOneLabelledSwitch() {
		val path = "/api/v1/notifications/mail-preferences"
		val backend = FakeBackend().apply {
			on(HttpMethod.Get, path) { """{"decisionsAboutMe":true,"moderationQueue":false}""" }
			on(HttpMethod.Put, path, HttpStatusCode.NoContent) { "" }
		}
		compose.setContent { MailPreferencesScreen(viewModel = MailPreferencesViewModel(backend.client()), onBack = {}) }
		compose.waitUntil(5_000) {
			compose.onAllNodesWithText("Sprawy do rozpatrzenia").fetchSemanticsNodes().isNotEmpty()
		}

		val row = compose.onNode(isToggleable() and hasText("Sprawy do rozpatrzenia"))
		row.assert(hasRole(Role.Switch))
		row.performClick()
		compose.waitForIdle()

		assertEquals("""{"decisionsAboutMe":true,"moderationQueue":true}""", backend.lastBody(HttpMethod.Put, path))
	}

	/** Błąd logowania był widoczny, ale czytnik go nie ogłaszał. */
	@Test
	fun loginErrorIsAnnounced() {
		val viewModel = LoginViewModel(
			FakeBackend().apply {
				on("/api/v1/auth/login", HttpStatusCode.Unauthorized) {
					"""{"code":"INVALID_CREDENTIALS","message":"Nieprawidłowy e-mail lub hasło",""" +
						""""timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/auth/login"}"""
				}
			}.client(),
		)
		compose.setContent { LoginScreen(viewModel = viewModel, onLoggedIn = {}, onNavigateToRegister = {}) }

		viewModel.onEmailChange("a@b.pl")
		viewModel.onPasswordChange("zlehaslo1")
		viewModel.submit()
		compose.waitUntil(5_000) {
			compose.onAllNodesWithText("Nieprawidłowy e-mail lub hasło").fetchSemanticsNodes().isNotEmpty()
		}

		compose.onNodeWithText("Nieprawidłowy e-mail lub hasło")
			.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
	}
}
