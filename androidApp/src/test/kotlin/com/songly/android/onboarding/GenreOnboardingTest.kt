package com.songly.android.onboarding

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.pageJson
import com.songly.android.profileJson
import io.ktor.http.HttpMethod
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class GenreOnboardingTest {

	@get:Rule(order = 0)
	val mainDispatcher = MainDispatcherRule()

	@get:Rule(order = 1)
	val compose = createComposeRule()

	private val myGenres = "/api/v1/profiles/me/genres"

	private class MemoryStore : OnboardingStore {
		val done = mutableSetOf<String>()
		override fun isDone(userId: String) = userId in done
		override fun markDone(userId: String) {
			done += userId
		}
	}

	private fun genreJson(id: String, name: String) = """{"id":"$id","name":"$name"}"""

	private fun backend(favorites: String = "[]") = FakeBackend().apply {
		onJson(
			"/api/v1/genres",
			pageJson(listOf(genreJson("g-pop", "Pop"), genreJson("g-jazz", "Jazz"), genreJson("g-rock", "Rock")).joinToString(","), 0, 1),
		)
		onJson("/api/v1/profiles/me", profileJson())
		on(HttpMethod.Get, myGenres) { favorites }
		on(HttpMethod.Put, myGenres) { "" }
	}

	private fun show(backend: FakeBackend, store: OnboardingStore, onFinished: (Boolean) -> Unit) {
		compose.setContent {
			GenreOnboardingScreen(GenreOnboardingViewModel(backend.client(), store), onFinished = onFinished)
		}
		compose.waitUntil(timeoutMillis = 5_000) {
			compose.onAllNodesWithText("Jazz").fetchSemanticsNodes().isNotEmpty()
		}
	}

	@Test
	fun savesExactlyTheChosenGenresAndRemembersIt() {
		val backend = backend()
		val store = MemoryStore()
		var finished: Boolean? = null
		show(backend, store) { finished = it }

		// Backend przyjmuje tylko niepusty zbiór — bez wyboru nie ma czego zapisać.
		compose.onNodeWithText("Wybierz co najmniej jeden").assertIsNotEnabled()
		compose.onNodeWithText("Jazz").performClick()
		compose.onNodeWithText("Pop").performClick()
		compose.onNodeWithText("Gotowe (2)").performClick()

		compose.waitUntil(timeoutMillis = 5_000) { finished != null }
		assertEquals(true, finished)
		val body = backend.lastBody(HttpMethod.Put, myGenres).orEmpty()
		assertTrue(body, "g-jazz" in body && "g-pop" in body && "g-rock" !in body)
		assertTrue(store.isDone("me-1"))
	}

	/** „Pomiń" to decyzja — zapamiętana, żeby nie pytać przy każdym logowaniu — ale niczego nie zapisuje. */
	@Test
	fun skipRemembersTheChoiceWithoutSavingGenres() {
		val backend = backend()
		val store = MemoryStore()
		var finished: Boolean? = null
		show(backend, store) { finished = it }

		compose.onNodeWithText("Pomiń").performClick()

		compose.waitUntil(timeoutMillis = 5_000) { finished != null }
		assertEquals(false, finished)
		assertEquals(0, backend.callCount(HttpMethod.Put, myGenres))
		assertTrue(store.isDone("me-1"))
	}

	@Test
	fun onboardingOnlyForAccountWithoutGenres() = runBlocking {
		val store = MemoryStore()

		assertTrue(needsGenreOnboarding(backend(favorites = "[]").client(), store))
		assertFalse(store.isDone("me-1"))

		// Gatunki ustawione wcześniej (np. na innym telefonie): bez onboardingu i od razu
		// zapamiętane, żeby kolejne starty nie pytały o nie serwera.
		assertFalse(needsGenreOnboarding(backend(favorites = "[${genreJson("g-pop", "Pop")}]").client(), store))
		assertTrue(store.isDone("me-1"))
	}

	@Test
	fun rememberedAccountIsNotAskedAgain() = runBlocking {
		val store = MemoryStore().apply { markDone("me-1") }
		val backend = backend(favorites = "[]")

		assertFalse(needsGenreOnboarding(backend.client(), store))
		assertEquals(0, backend.callCount(myGenres))
	}
}
