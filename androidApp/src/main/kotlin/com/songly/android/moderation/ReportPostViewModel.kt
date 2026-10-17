package com.songly.android.moderation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.launch

private const val MAX_DETAILS_LENGTH = 500

data class ReportPostUiState(
	val reason: String? = null,
	val details: String = "",
	val isSubmitting: Boolean = false,
	val errorMessage: String? = null,
	val submitted: Boolean = false,
) {
	val canSubmit: Boolean
		get() = reason != null && details.length <= MAX_DETAILS_LENGTH && !isSubmitting
}

/**
 * Zgłoszenie posta w społeczności. Trafia do kolejki moderatorów, a ci przy
 * pierwszym zgłoszeniu posta dostają powiadomienie (ADR 0012).
 */
class ReportPostViewModel(
	private val client: SonglyApiClient,
	private val postId: String,
) : ViewModel() {

	var uiState by mutableStateOf(ReportPostUiState())
		private set

	fun selectReason(reason: String) {
		uiState = uiState.copy(reason = reason, errorMessage = null)
	}

	fun onDetailsChange(value: String) {
		uiState = uiState.copy(details = value, errorMessage = null)
	}

	fun submit() {
		val reason = uiState.reason ?: return
		if (!uiState.canSubmit) return
		uiState = uiState.copy(isSubmitting = true, errorMessage = null)
		viewModelScope.launch {
			uiState = try {
				client.reportPost(postId, reason, uiState.details.trim().ifBlank { null })
				uiState.copy(isSubmitting = false, submitted = true)
			} catch (e: SonglyApiException) {
				uiState.copy(isSubmitting = false, errorMessage = reportErrorText(e))
			} catch (e: Exception) {
				uiState.copy(isSubmitting = false, errorMessage = "Brak połączenia z serwerem.")
			}
		}
	}

	private fun reportErrorText(e: SonglyApiException): String = when (e.error?.code) {
		// Limit zależy od historii trafności zgłoszeń (ADR 0025) — nie podajemy liczb,
		// bo i tak nie są stałe.
		"REPORT_RATE_LIMIT_EXCEEDED" -> "Zgłaszasz za często. Spróbuj ponownie później."
		else -> e.error?.message ?: "Nie udało się zgłosić posta (HTTP ${e.status})"
	}
}

/** Polskie etykiety powodów zgłoszenia. */
fun reportReasonLabel(reason: String): String = when (reason) {
	"SPAM" -> "Spam"
	"HARASSMENT" -> "Nękanie"
	"HATE_SPEECH" -> "Mowa nienawiści"
	"ILLEGAL_CONTENT" -> "Treść nielegalna"
	"OTHER" -> "Inny powód"
	else -> reason
}
