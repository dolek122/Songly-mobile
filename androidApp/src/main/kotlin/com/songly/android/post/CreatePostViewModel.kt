package com.songly.android.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) {
	val remaining: Int
		get() = MAX_POST_LENGTH - content.length

	val canSubmit: Boolean
		get() = content.isNotBlank() && remaining >= 0 && !isSubmitting
}

/**
 * Logika ekranu tworzenia posta. Woła [SonglyApiClient.createPost] z modułu
 * `shared`. Sukces sygnalizujemy callbackiem [submit] (a nie flagą w stanie) —
 * ViewModel bywa cache'owany na Activity, więc flaga „utworzono" utrzymałaby się
 * między wejściami i od razu zamykała ekran.
 */
class CreatePostViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(CreatePostUiState())
		private set

	fun onContentChange(value: String) {
		uiState = uiState.copy(content = value, errorMessage = null)
	}

	fun submit(onCreated: () -> Unit) {
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null)
		viewModelScope.launch {
			try {
				client.createPost(uiState.content.trim())
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
