package com.songly.android.comments

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.communityJson
import com.songly.android.pageJson
import com.songly.android.postJson
import com.songly.android.profileJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Moderator społeczności usuwa cudze komentarze pod postami w niej (backend ADR
 * 0045). Uprawnienie wynika ze społeczności posta, nie z samego posta, więc
 * sprawdzamy wszystkie trzy przypadki: moderator, zwykły członek, post poza
 * społecznością.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommentModerationTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private fun commentJson(id: String, authorId: String): String =
		"""{"id":"$id","postId":"p1","authorId":"$authorId","authorDisplayName":"Autor","authorAvatarUrl":null,""" +
			""""content":"treść","createdAt":"2026-01-01T10:00:00Z"}"""

	private fun backend(communityId: String?, myRole: String? = null) = FakeBackend().apply {
		onJson(
			"/api/v1/posts/p1/comments",
			pageJson("${commentJson("own", "me-1")},${commentJson("other", "other-1")}", number = 0, totalPages = 1),
		)
		onJson("/api/v1/profiles/me", profileJson("me-1"))
		val post = postJson("p1", "post", authorId = "author-1", authorName = "Autor")
		onJson("/api/v1/posts/p1", if (communityId == null) post else post.dropLast(1) + ""","communityId":"$communityId"}""")
		if (communityId != null) onJson("/api/v1/communities/$communityId", communityJson(communityId, myRole))
	}

	private fun loaded(backend: FakeBackend): CommentsUiState {
		val viewModel = CommentsViewModel(backend.client(), "p1")
		viewModel.load()
		val deadline = System.currentTimeMillis() + 5_000
		while (viewModel.uiState.isLoading && System.currentTimeMillis() < deadline) Thread.sleep(20)
		return viewModel.uiState
	}

	private fun CommentsUiState.comment(id: String) = comments.first { it.id == id }

	@Test
	fun moderatorOfPostsCommunity_canRemoveOthersComments() {
		val state = loaded(backend(communityId = "c1", myRole = "MODERATOR"))

		assertTrue(state.canDelete(state.comment("other")))
		assertTrue(state.isModeration(state.comment("other")))
		// Własny komentarz moderatora to zwykłe usunięcie, bez decyzji i powiadomienia.
		assertTrue(state.canDelete(state.comment("own")))
		assertFalse(state.isModeration(state.comment("own")))
	}

	@Test
	fun regularMember_canRemoveOnlyOwnComments() {
		val state = loaded(backend(communityId = "c1", myRole = "MEMBER"))

		assertTrue(state.canDelete(state.comment("own")))
		assertFalse(state.canDelete(state.comment("other")))
	}

	/** Pod zwykłym postem chroni samo autorstwo — także przed autorem posta. */
	@Test
	fun postOutsideCommunity_allowsOnlyOwnComments() {
		val state = loaded(backend(communityId = null))

		assertTrue(state.canDelete(state.comment("own")))
		assertFalse(state.canDelete(state.comment("other")))
	}
}
