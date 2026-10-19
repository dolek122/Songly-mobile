package com.songly.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Obrót ekranu (i zmiana motywu) niszczy Activity i tworzy ją od nowa, a ViewModele
 * przeżywają to bez zmian. Dwa błędy, które to wywoływało:
 *  - klient HTTP zamykany razem z Activity, a dalej używany przez ViewModele —
 *    każda akcja po obrocie kończyła się „Brak połączenia z serwerem",
 *  - nawigacja w zwykłym `remember` — obrót wyrzucał z każdego ekranu na feed.
 *
 * Test jedzie przez całą `MainActivity`, bo oba błędy siedziały na styku Activity,
 * kompozycji i ViewModeli; test pojedynczego ekranu żadnego z nich nie widzi.
 *
 * Wymaga `forkEvery = 1` w `build.gradle.kts` — jako pierwszy alfabetycznie test z
 * Compose odsłonił przeciek globalnego stanu Compose między klasami (opis tamże).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestSonglyApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
class ConfigurationChangeTest {

	private val backend = FakeBackend().apply {
		onJson(
			"/api/v1/feed",
			pageJson(postJson("p1", "Pierwszy post", authorId = "u2", authorName = "Ola"), number = 0, totalPages = 1),
		)
		onJson("/api/v1/profiles/me", profileJson())
		onJson("/api/v1/notifications/unread-count", """{"count":0}""")
		onJson("/api/v1/notifications", emptyPage)
		onJson("/api/v1/profiles/u2", """{"userId":"u2","displayName":"Ola","bio":"Profil Oli","favoriteGenres":[]}""")
		onJson("/api/v1/posts", emptyPage)
		on(HttpMethod.Put, "/api/v1/posts/p1/likes", HttpStatusCode.NoContent) { "" }
	}

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	// Atrapa musi być na miejscu, zanim reguła Compose uruchomi Activity — a ta
	// startuje jeszcze przed metodami @Before.
	@get:Rule(order = 1)
	val installBackend = object : ExternalResource() {
		override fun before() {
			TestSonglyApplication.backend = backend
		}
	}

	@get:Rule(order = 2)
	val compose = createAndroidComposeRule<MainActivity>()

	private fun waitForText(text: String) {
		compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
	}

	private fun waitForDescription(description: String) {
		compose.waitUntil(5_000) {
			compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
		}
	}

	private fun rotate() {
		compose.activityRule.scenario.recreate()
	}

	@Test
	fun requestsStillWorkAfterRotation() {
		waitForText("Pierwszy post")

		rotate()
		waitForDescription("Polubienia: 0")
		compose.onNodeWithContentDescription("Polubienia: 0").performClick()
		compose.waitForIdle()

		// Zamknięty klient nie dociera do serwera, a lajk cofa się po błędzie.
		assertEquals(1, backend.callCount(HttpMethod.Put, "/api/v1/posts/p1/likes"))
		compose.onNodeWithContentDescription("Polubienia: 1").assertExists()
	}

	@Test
	fun openScreenSurvivesRotation() {
		waitForText("Pierwszy post")
		compose.onNodeWithContentDescription("Powiadomienia").performClick()
		waitForText("Brak powiadomień.")

		rotate()

		waitForText("Brak powiadomień.")
		assertEquals(0, compose.onAllNodesWithText("Pierwszy post").fetchSemanticsNodes().size)
	}

	@Test
	fun overlayStackSurvivesRotation() {
		waitForText("Pierwszy post")
		compose.onNodeWithText("Ola").performClick()
		waitForText("Profil Oli")

		rotate()

		// Nakładki mają własny zapis (rodzaj:id) — to on musi odtworzyć profil.
		waitForText("Profil Oli")
		assertEquals(0, compose.onAllNodesWithText("Pierwszy post").fetchSemanticsNodes().size)
	}
}
