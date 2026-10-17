package com.songly.android.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.android.media.PreparedImage
import com.songly.shared.model.PostImage
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

/** Maksymalna długość treści posta — zgodna z walidacją backendu (@Size max 1000). */
const val MAX_POST_LENGTH = 1000

/** Stan ekranu tworzenia posta. */
data class CreatePostUiState(
	val content: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	/** Zdjęcie już wgrane na serwer — do publikacji idzie jego klucz. */
	val image: PostImage? = null,
	val isUploadingImage: Boolean = false,
) {
	val remaining: Int
		get() = MAX_POST_LENGTH - content.length

	// Czekamy na koniec wgrywania — inaczej post wyszedłby bez zdjęcia, które
	// użytkownik właśnie dodał.
	val canSubmit: Boolean
		get() = content.isNotBlank() && remaining >= 0 && !isSubmitting && !isUploadingImage
}

/**
 * Logika ekranu tworzenia posta. Woła [SonglyApiClient.createPost] z modułu
 * `shared`. Sukces sygnalizujemy callbackiem [submit] (a nie flagą w stanie) —
 * ViewModel bywa cache'owany na Activity, więc flaga „utworzono" utrzymałaby się
 * między wejściami i od razu zamykała ekran.
 *
 * Zdjęcie wgrywamy od razu po wybraniu (ADR 0010: najpierw `POST /posts/image`,
 * potem post z kluczem), więc przy publikacji jest już tylko jedno szybkie
 * żądanie, a błąd formatu czy rozmiaru wychodzi, zanim ktoś napisze treść.
 */
class CreatePostViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(CreatePostUiState())
		private set

	fun onContentChange(value: String) {
		uiState = uiState.copy(content = value, errorMessage = null)
	}

	fun attachImage(prepare: suspend () -> PreparedImage) {
		if (uiState.isUploadingImage) return
		uiState = uiState.copy(isUploadingImage = true, errorMessage = null)
		viewModelScope.launch {
			uiState = try {
				val prepared = prepare()
				uiState.copy(isUploadingImage = false, image = client.uploadPostImage(prepared.bytes, prepared.contentType))
			} catch (e: IllegalArgumentException) {
				uiState.copy(isUploadingImage = false, errorMessage = "Nie udało się odczytać wybranego zdjęcia.")
			} catch (e: SonglyApiException) {
				uiState.copy(
					isUploadingImage = false,
					errorMessage = e.error?.message ?: "Nie udało się wgrać zdjęcia (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState.copy(isUploadingImage = false, errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.")
			}
		}
	}

	fun removeImage() {
		uiState = uiState.copy(image = null)
	}

	fun submit(onCreated: () -> Unit) {
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null)
		viewModelScope.launch {
			try {
				client.createPost(uiState.content.trim(), uiState.image?.key)
				uiState = CreatePostUiState() // reset — czysty ekran przy następnym wejściu
				onCreated()
			} catch (e: SonglyApiException) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = e.error?.message ?: "Nie udało się dodać posta (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.",
				)
			}
		}
	}
}
