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

/**
 * Stan ekranu „znajomi teraz słuchają".
 *
 * [connecting] rozróżnia „jeszcze się nie połączyliśmy ani razu" od „połączenie
 * zerwane, ale mamy ostatnio znane statusy" — bez tego pierwsze wejście na ekran
 * i chwilowa utrata sieci wyglądałyby identycznie (pusta lista).
 */
data class FriendsPresenceUiState(
	val friends: List<FriendNowPlaying> = emptyList(),
	val live: Boolean = false,
	val connecting: Boolean = true,
	val problem: PresenceProblem? = null,
)

/**
 * Zbiera strumień presence z `shared` (SSE + reconnect z backoffem siedzą tam,
 * żeby iOS dostał dokładnie to samo zachowanie). ViewModel tylko przepisuje
 * kolejne migawki na stan Compose.
 */
class FriendsPresenceViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FriendsPresenceUiState())
		private set

	private var streamJob: Job? = null

	/**
	 * Podłącza strumień. Wołane przy wejściu na ekran; powtórne wywołanie nie
	 * mnoży połączeń — backend limituje ich liczbę na użytkownika, a i tak
	 * wystarczy jedno.
	 */
	fun start() {
		if (streamJob?.isActive == true) return
		streamJob = viewModelScope.launch {
			client.friendsPresence().collect { presence ->
				uiState = FriendsPresenceUiState(
					friends = presence.friends,
					live = presence.live,
					// Limit strumieni nie jest „jeszcze się łączymy" tylko konkretną
					// odmową — pokazujemy go od razu, zamiast udawać ładowanie.
					connecting = !presence.live && presence.problem != PresenceProblem.TOO_MANY_STREAMS &&
						uiState.friends.isEmpty() && presence.friends.isEmpty(),
					problem = presence.problem,
				)
			}
		}
	}

	/**
	 * Strumień kończy się razem z [viewModelScope], ale ekran zamykany ręcznie
	 * zwalnia połączenie od razu — po co trzymać otwarte gniazdo, którego nikt nie
	 * ogląda.
	 */
	fun stop() {
		streamJob?.cancel()
		streamJob = null
	}

	override fun onCleared() {
		super.onCleared()
		stop()
	}
}
