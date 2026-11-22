package com.songly.android.presence

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.presence.PresenceProblem
import com.songly.shared.network.SonglyApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class FriendsPresenceUiState(
	val friends: List<FriendNowPlaying> = emptyList(),
	val live: Boolean = false,
	val connecting: Boolean = true,
	val problem: PresenceProblem? = null,
)

class FriendsPresenceViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FriendsPresenceUiState())
		private set

	private var streamJob: Job? = null

	fun start() {
		if (streamJob?.isActive == true) return
		streamJob = viewModelScope.launch {
			client.friendsPresence().collect { presence ->
				uiState = FriendsPresenceUiState(
					friends = presence.friends,
					live = presence.live,
					connecting = !presence.live && presence.problem != PresenceProblem.TOO_MANY_STREAMS &&
						uiState.friends.isEmpty() && presence.friends.isEmpty(),
					problem = presence.problem,
				)
			}
		}
	}

	fun stop() {
		streamJob?.cancel()
		streamJob = null
	}

	override fun onCleared() {
		super.onCleared()
		stop()
	}
}
