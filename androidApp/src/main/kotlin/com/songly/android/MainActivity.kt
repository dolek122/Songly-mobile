package com.songly.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.songly.android.auth.LoginScreen
import com.songly.android.auth.LoginViewModel
import com.songly.android.auth.RegisterScreen
import com.songly.android.auth.RegisterViewModel
import com.songly.android.comments.CommentsScreen
import com.songly.android.comments.CommentsViewModel
import com.songly.android.communities.CommunitiesScreen
import com.songly.android.communities.CommunitiesViewModel
import com.songly.android.communities.CommunityDetailScreen
import com.songly.android.communities.CommunityDetailViewModel
import com.songly.android.feed.FeedScreen
import com.songly.android.feed.FeedViewModel
import com.songly.android.friends.FriendsScreen
import com.songly.android.friends.FriendsViewModel
import com.songly.android.matches.MatchesScreen
import com.songly.android.matches.MatchesViewModel
import com.songly.android.notifications.NotificationsScreen
import com.songly.android.notifications.NotificationsViewModel
import com.songly.android.nowplaying.NowPlayingScreen
import com.songly.android.nowplaying.NowPlayingViewModel
import com.songly.android.presence.FriendsPresenceScreen
import com.songly.android.presence.FriendsPresenceViewModel
import com.songly.android.post.CreatePostScreen
import com.songly.android.post.CreatePostViewModel
import com.songly.android.profile.ProfileScreen
import com.songly.android.profile.ProfileViewModel
import com.songly.android.profile.UserProfileScreen
import com.songly.android.profile.UserProfileViewModel
import com.songly.android.ui.theme.SonglyTheme
import com.songly.shared.config.ApiConfig
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.session.AndroidKeystoreTokenStorage

/**
 * Punkt wejścia aplikacji Androida. UI natywny (Jetpack Compose, §14); logika
 * sieci i sesji żyje w module `shared` (KMP), współdzielona z iOS.
 *
 * [SonglyApiClient] tworzymy raz na Activity (trzyma sesję i klienta HTTP) i
 * zamykamy w [onDestroy]. Docelowo wstrzykniemy go przez DI zamiast trzymać w
 * Activity — na tym etapie prostota wygrywa.
 */
class MainActivity : ComponentActivity() {

	private val apiClient: SonglyApiClient by lazy {
		SonglyApiClient(
			config = ApiConfig.LOCAL_ANDROID_EMULATOR,
			storage = AndroidKeystoreTokenStorage(applicationContext),
		)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			SonglyTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					AppRoot(apiClient)
				}
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		apiClient.close()
	}
}

@Composable
private fun AppRoot(client: SonglyApiClient) {
	// null = jeszcze sprawdzamy zapisaną sesję (Keystore, I/O poza głównym wątkiem).
	var loggedIn by remember { mutableStateOf<Boolean?>(null) }

	LaunchedEffect(Unit) {
		loggedIn = client.isLoggedIn()
	}

	when (loggedIn) {
		null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}

		true -> LoggedInNav(client = client, onLoggedOut = { loggedIn = false })

		false -> AuthNav(client = client, onAuthenticated = { loggedIn = true })
	}
}

/**
 * Nawigacja przed zalogowaniem: logowanie ↔ rejestracja. Oba ekrany po sukcesie
 * wywołują [onAuthenticated] (sesja jest już założona w `shared`). Prosty stan
 * zamiast Navigation-Compose — wystarcza na dwa ekrany, jak w [LoggedInNav].
 */
@Composable
private fun AuthNav(client: SonglyApiClient, onAuthenticated: () -> Unit) {
	var registering by remember { mutableStateOf(false) }

	// Wstecz z rejestracji wraca do logowania, a nie zamyka aplikację.
	BackHandler(enabled = registering) { registering = false }

	if (registering) {
		val registerViewModel: RegisterViewModel = viewModel(
			factory = viewModelFactory {
				initializer { RegisterViewModel(client) }
			},
		)
		RegisterScreen(
			viewModel = registerViewModel,
			onRegistered = onAuthenticated,
			onNavigateToLogin = { registering = false },
		)
	} else {
		val loginViewModel: LoginViewModel = viewModel(
			factory = viewModelFactory {
				initializer { LoginViewModel(client) }
			},
		)
		LoginScreen(
			viewModel = loginViewModel,
			onLoggedIn = onAuthenticated,
			onNavigateToRegister = { registering = true },
		)
	}
}

/**
 * Nawigacja w obrębie zalogowanej sesji: feed ↔ tworzenie posta. [feedViewModel]
 * jest tu (a nie w gałęzi feedu), żeby po dodaniu posta odświeżyć feed tą samą
 * instancją. Prosty stan zamiast Navigation-Compose — wystarcza na dwa ekrany.
 */
@Composable
private fun LoggedInNav(client: SonglyApiClient, onLoggedOut: () -> Unit) {
	val feedViewModel: FeedViewModel = viewModel(
		factory = viewModelFactory {
			initializer { FeedViewModel(client) }
		},
	)
	var creatingPost by remember { mutableStateOf(false) }
	var commentsPostId by remember { mutableStateOf<String?>(null) }
	var showProfile by remember { mutableStateOf(false) }
	var showFriends by remember { mutableStateOf(false) }
	var showNowPlaying by remember { mutableStateOf(false) }
	var showFriendsPresence by remember { mutableStateOf(false) }
	var showNotifications by remember { mutableStateOf(false) }
	var showCommunities by remember { mutableStateOf(false) }
	var communityDetailId by remember { mutableStateOf<String?>(null) }
	var showMatches by remember { mutableStateOf(false) }
	// Profil innego użytkownika — nakładka nad dowolnym ekranem (feed, znajomi…);
	// po powrocie (viewUserId = null) wracamy do ekranu spod spodu.
	var viewUserId by remember { mutableStateOf<String?>(null) }

	// Systemowy Wstecz (przycisk i gest) musi robić to samo co „Wróć" na ekranie —
	// bez tego zamyka aplikację, co na Androidzie jest po prostu zepsute.
	//
	// Kolejność MUSI odpowiadać kolejności gałęzi `when` poniżej: zamykamy ekran,
	// który jest naprawdę na wierzchu, a nie pierwszą podniesioną flagę. Gdy
	// ekranów będzie więcej, ta duplikacja jest sygnałem, że czas na
	// Navigation-Compose z prawdziwym back stackiem.
	val closeTopScreen: (() -> Unit)? = when {
		viewUserId != null -> ({ viewUserId = null })
		showMatches -> ({ showMatches = false })
		communityDetailId != null -> ({ communityDetailId = null })
		showCommunities -> ({ showCommunities = false })
		// Powrót z powiadomień odświeża feed tak samo jak „Wróć" — inaczej badge
		// nieprzeczytanych zostawałby po lekturze.
		showNotifications -> ({ showNotifications = false; feedViewModel.refresh() })
		showFriendsPresence -> ({ showFriendsPresence = false })
		showNowPlaying -> ({ showNowPlaying = false })
		showProfile -> ({ showProfile = false })
		showFriends -> ({ showFriends = false })
		creatingPost -> ({ creatingPost = false })
		commentsPostId != null -> ({ commentsPostId = null })
		// Na feedzie zostawiamy Wstecz systemowi — tam wyjście z aplikacji jest
		// zachowaniem oczekiwanym.
		else -> null
	}
	BackHandler(enabled = closeTopScreen != null) { closeTopScreen?.invoke() }

	when {
		viewUserId != null -> {
			val id = viewUserId!!
			// Klucz per użytkownik — inaczej viewModel() oddałby profil innej osoby.
			val userProfileViewModel: UserProfileViewModel = viewModel(
				key = "user-profile-$id",
				factory = viewModelFactory {
					initializer { UserProfileViewModel(client, id) }
				},
			)
			UserProfileScreen(
				viewModel = userProfileViewModel,
				onBack = { viewUserId = null },
			)
		}

		showMatches -> {
			val matchesViewModel: MatchesViewModel = viewModel(
				factory = viewModelFactory {
					initializer { MatchesViewModel(client) }
				},
			)
			MatchesScreen(
				viewModel = matchesViewModel,
				onBack = { showMatches = false },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		communityDetailId != null -> {
			val id = communityDetailId!!
			// Klucz per społeczność — inaczej viewModel() oddałby dane innej.
			val detailViewModel: CommunityDetailViewModel = viewModel(
				key = "community-$id",
				factory = viewModelFactory {
					initializer { CommunityDetailViewModel(client, id) }
				},
			)
			CommunityDetailScreen(
				viewModel = detailViewModel,
				onBack = { communityDetailId = null },
				// Po usunięciu wróć do listy — CommunitiesScreen odświeży się sam przy wejściu.
				onDeleted = { communityDetailId = null },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		showCommunities -> {
			val communitiesViewModel: CommunitiesViewModel = viewModel(
				factory = viewModelFactory {
					initializer { CommunitiesViewModel(client) }
				},
			)
			CommunitiesScreen(
				viewModel = communitiesViewModel,
				onBack = { showCommunities = false },
				onOpenCommunity = { communityDetailId = it },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		showNotifications -> {
			val notificationsViewModel: NotificationsViewModel = viewModel(
				factory = viewModelFactory {
					initializer { NotificationsViewModel(client) }
				},
			)
			NotificationsScreen(
				viewModel = notificationsViewModel,
				// Powrót odświeża feed, żeby badge nieprzeczytanych zszedł po lekturze.
				onBack = { showNotifications = false; feedViewModel.refresh() },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		showFriendsPresence -> {
			val presenceViewModel: FriendsPresenceViewModel = viewModel(
				factory = viewModelFactory {
					initializer { FriendsPresenceViewModel(client) }
				},
			)
			FriendsPresenceScreen(
				viewModel = presenceViewModel,
				onBack = { showFriendsPresence = false },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		showNowPlaying -> {
			val nowPlayingViewModel: NowPlayingViewModel = viewModel(
				factory = viewModelFactory {
					initializer { NowPlayingViewModel(client) }
				},
			)
			NowPlayingScreen(
				viewModel = nowPlayingViewModel,
				onBack = { showNowPlaying = false },
				onOpenFriendsPresence = { showFriendsPresence = true },
			)
		}

		showProfile -> {
			val profileViewModel: ProfileViewModel = viewModel(
				factory = viewModelFactory {
					initializer { ProfileViewModel(client) }
				},
			)
			ProfileScreen(
				viewModel = profileViewModel,
				onBack = { showProfile = false },
				onAccountDeleted = { showProfile = false; onLoggedOut() },
			)
		}

		showFriends -> {
			val friendsViewModel: FriendsViewModel = viewModel(
				factory = viewModelFactory {
					initializer { FriendsViewModel(client) }
				},
			)
			FriendsScreen(
				viewModel = friendsViewModel,
				onBack = { showFriends = false },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		creatingPost -> {
			val createPostViewModel: CreatePostViewModel = viewModel(
				factory = viewModelFactory {
					initializer { CreatePostViewModel(client) }
				},
			)
			CreatePostScreen(
				viewModel = createPostViewModel,
				onCancel = { creatingPost = false },
				onCreated = {
					creatingPost = false
					feedViewModel.refresh()
				},
			)
		}

		commentsPostId != null -> {
			val postId = commentsPostId!!
			// Klucz per post — inaczej viewModel() oddałby komentarze innego posta.
			val commentsViewModel: CommentsViewModel = viewModel(
				key = "comments-$postId",
				factory = viewModelFactory {
					initializer { CommentsViewModel(client, postId) }
				},
			)
			CommentsScreen(
				viewModel = commentsViewModel,
				onBack = { commentsPostId = null },
				onCommentAdded = { feedViewModel.incrementCommentCount(postId) },
				onCommentDeleted = { feedViewModel.decrementCommentCount(postId) },
				onOpenUserProfile = { viewUserId = it },
			)
		}

		else -> {
			FeedScreen(
				viewModel = feedViewModel,
				client = client,
				onLoggedOut = onLoggedOut,
				onSessionExpired = onLoggedOut,
				onCreatePost = { creatingPost = true },
				onOpenComments = { commentsPostId = it },
				onOpenUserProfile = { viewUserId = it },
				onOpenProfile = { showProfile = true },
				onOpenFriends = { showFriends = true },
				onOpenNowPlaying = { showNowPlaying = true },
				onOpenFriendsPresence = { showFriendsPresence = true },
				onOpenNotifications = { showNotifications = true },
				onOpenCommunities = { showCommunities = true },
				onOpenMatches = { showMatches = true },
			)
		}
	}
}
