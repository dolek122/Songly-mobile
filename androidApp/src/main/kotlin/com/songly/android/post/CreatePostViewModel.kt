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

const val MAX_POST_LENGTH = 1000

private const val UNKNOWN_POST_IMAGE = "UNKNOWN_POST_IMAGE"

internal const val EXPIRED_IMAGE_MESSAGE = "Zdjęcie wygasło. Dodaj je jeszcze raz."

data class CreatePostUiState(
	val content: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val image: PostImage? = null,
	val isUploadingImage: Boolean = false,
) {
	val remaining: Int
		get() = MAX_POST_LENGTH - content.length

	val canSubmit: Boolean
		get() = content.isNotBlank() && remaining >= 0 && !isSubmitting && !isUploadingImage
}

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
				uiState = CreatePostUiState()
				onCreated()
			} catch (e: SonglyApiException) {
				uiState = if (e.error?.code == UNKNOWN_POST_IMAGE) {
					uiState.copy(isSubmitting = false, image = null, errorMessage = EXPIRED_IMAGE_MESSAGE)
				} else {
					uiState.copy(
						isSubmitting = false,
						errorMessage = e.error?.message ?: "Nie udało się dodać posta (HTTP ${e.status})",
					)
				}
			} catch (e: Exception) {
				uiState = uiState.copy(
					isSubmitting = false,
					errorMessage = "Brak połączenia z serwerem. Spróbuj ponownie.",
				)
			}
		}
	}
}
