package com.songly.android.post

import com.songly.android.FakeBackend
import com.songly.android.MainDispatcherRule
import com.songly.android.media.PreparedImage
import com.songly.android.postJson
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Zdjęcie w poście: najpierw upload (klucz), potem post z tym kluczem (ADR 0010). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreatePostImageTest {

	@get:Rule
	val mainDispatcher = MainDispatcherRule()

	private val image = PreparedImage(byteArrayOf(1, 2, 3), "image/jpeg")

	private fun backend() = FakeBackend().apply {
		onJson("/api/v1/posts/image", """{"key":"posts/me-1/abc.jpg","url":"http://cdn/posts/me-1/abc.jpg"}""")
		on(HttpMethod.Post, "/api/v1/posts", HttpStatusCode.Created) { postJson("p1", "z fotką") }
	}

	private fun awaitUntil(condition: () -> Boolean) {
		val deadline = System.currentTimeMillis() + 5_000
		while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
	}

	@Test
	fun publishedPostCarriesUploadedImageKey() {
		val backend = backend()
		val viewModel = CreatePostViewModel(backend.client())
		var created = false

		viewModel.onContentChange("z fotką")
		viewModel.attachImage { image }
		awaitUntil { viewModel.uiState.image != null }
		viewModel.submit { created = true }
		awaitUntil { created }

		val body = backend.lastBody(HttpMethod.Post, "/api/v1/posts")!!
		assertTrue(body, body.contains("\"imageKey\":\"posts/me-1/abc.jpg\""))
	}

	@Test
	fun removedImageIsNotAttached() {
		val backend = backend()
		val viewModel = CreatePostViewModel(backend.client())
		var created = false

		viewModel.onContentChange("bez fotki")
		viewModel.attachImage { image }
		awaitUntil { viewModel.uiState.image != null }
		viewModel.removeImage()
		viewModel.submit { created = true }
		awaitUntil { created }

		assertFalse(backend.lastBody(HttpMethod.Post, "/api/v1/posts")!!.contains("imageKey"))
	}

	/** Backend odrzuca format — komunikat serwera trafia do użytkownika, post nie wychodzi. */
	@Test
	fun rejectedImageShowsServerMessage() {
		val backend = FakeBackend().apply {
			on("/api/v1/posts/image", HttpStatusCode.UnsupportedMediaType) {
				"""{"code":"UNSUPPORTED_IMAGE","message":"Obsługiwane formaty obrazu to JPEG, PNG i WebP",""" +
					""""timestamp":"2026-01-01T00:00:00Z","path":"/api/v1/posts/image"}"""
			}
		}
		val viewModel = CreatePostViewModel(backend.client())

		viewModel.attachImage { image }
		awaitUntil { !viewModel.uiState.isUploadingImage }

		assertNull(viewModel.uiState.image)
		assertEquals("Obsługiwane formaty obrazu to JPEG, PNG i WebP", viewModel.uiState.errorMessage)
	}
}
