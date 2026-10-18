package com.songly.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.songly.android.account.SuspendedScreen
import com.songly.android.account.SuspendedViewModel
import com.songly.android.admin.AdminScreen
import com.songly.android.admin.AdminViewModel
import com.songly.android.auth.ForgotPasswordScreen
import com.songly.android.auth.ForgotPasswordViewModel
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
import com.songly.android.moderation.CommunityModerationViewModel
import com.songly.android.moderation.ReportPostViewModel
import com.songly.android.matches.MatchesScreen
import com.songly.android.matches.MatchesViewModel
import com.songly.android.notifications.MailPreferencesScreen
import com.songly.android.notifications.MailPreferencesViewModel
import com.songly.android.notifications.NotificationsScreen
import com.songly.android.notifications.NotificationsViewModel
import com.songly.android.nowplaying.NowPlayingScreen
import com.songly.android.nowplaying.NowPlayingViewModel
import com.songly.android.presence.FriendsPresenceScreen
import com.songly.android.presence.FriendsPresenceViewModel
import com.songly.android.post.CreatePostScreen
import com.songly.android.post.CreatePostViewModel
import com.songly.android.post.PostDetailScreen
import com.songly.android.post.PostDetailViewModel
import com.songly.android.post.PostListViewModel
import com.songly.android.profile.ProfileScreen
import com.songly.android.profile.ProfileViewModel
import com.songly.android.profile.UserProfileScreen
import com.songly.android.profile.UserProfileViewModel
import com.songly.android.ui.theme.SonglyTheme
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.util.AuthLink
import com.songly.shared.util.SpotifyConnectResult
import kotlinx.coroutines.launch

/**
 * Punkt wejścia aplikacji Androida. UI natywny (Jetpack Compose, §14); logika
 * sieci i sesji żyje w module `shared` (KMP), współdzielona z iOS.
 *
 * [SonglyApiClient] należy do [SonglyApplication], nie do Activity — Activity ginie
 * przy każdym obrocie ekranu, a ViewModele, które trzymają klienta, nie.
 *
 * Stan nawigacji jest w `rememberSaveable`: zwykłe `remember` znika razem z
 * Activity, więc obrót ekranu wyrzucał z dowolnego miejsca z powrotem na feed.
 */
class MainActivity : ComponentActivity() {

	// Link z maila czekający na obsłużenie (ADR 0037 backendu). Trzymany w Activity,
	// nie w kompozycji, bo przy otwartej aplikacji przychodzi przez onNewIntent.
	private var pendingAuthLink by mutableStateOf<AuthLink?>(null)

	// Wynik OAuth Spotify z linku powrotnego (ADR 0038 backendu), z tych samych
	// powodów trzymany w Activity co link z maila.
	private var pendingSpotifyResult by mutableStateOf<SpotifyConnectResult?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// Po obrocie albo odtworzeniu procesu Activity dostaje ten sam intent jeszcze
		// raz — obsłużony już link nie może potwierdzać adresu drugi raz.
		if (savedInstanceState == null) takeLink(intent)
		val apiClient = (application as SonglyApplication).apiClient
		setContent {
			SonglyTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					AppRoot(
						apiClient,
						authLink = pendingAuthLink,
						onAuthLinkHandled = { pendingAuthLink = null },
						spotifyResult = pendingSpotifyResult,
						onSpotifyResultHandled = { pendingSpotifyResult = null },
					)
				}
			}
		}
	}

	// launchMode="singleTask": link otwarty przy działającej aplikacji trafia tutaj,
	// zamiast tworzyć drugą kopię aplikacji nad pierwszą.
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		takeLink(intent)
	}

	private fun takeLink(intent: Intent?) {
		val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
		AuthLink.parse(url)?.let { pendingAuthLink = it }
		SpotifyConnectResult.parse(url)?.let { pendingSpotifyResult = it }
	}
}

@Composable
private fun AppRoot(
	client: SonglyApiClient,
	authLink: AuthLink?,
	onAuthLinkHandled: () -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
) {
	// null = jeszcze sprawdzamy zapisaną sesję (Keystore, I/O poza głównym wątkiem).
	var loggedIn by rememberSaveable { mutableStateOf<Boolean?>(null) }
	// Powód powrotu do logowania (np. zmiana hasła odwołała sesję) — bez niego
	// użytkownik zostałby nagle wylogowany bez słowa wyjaśnienia.
	var authNotice by rememberSaveable { mutableStateOf<String?>(null) }
	val scope = rememberCoroutineScope()

	LaunchedEffect(Unit) {
		loggedIn = client.isLoggedIn()
	}

	// Każde zalogowanie i wylogowanie to nowe pokolenie ViewModeli (SessionViewModels).
	var generation by rememberSaveable { mutableStateOf(0) }
	val changeLoggedIn: (Boolean) -> Unit = { value ->
		if (value != loggedIn) generation++
		loggedIn = value
	}

	SessionViewModels(generation) {
		AuthOrApp(
			client = client,
			loggedIn = loggedIn,
			authNotice = authNotice,
			onAuthNotice = { authNotice = it },
			onLoggedInChange = changeLoggedIn,
			onLogoutForLink = {
				// Link zostaje oczekujący — po wylogowaniu obsłuży go AuthNav.
				scope.launch {
					client.logout()
					authNotice = null
					changeLoggedIn(false)
				}
			},
			authLink = authLink,
			onAuthLinkHandled = onAuthLinkHandled,
			spotifyResult = spotifyResult,
			onSpotifyResultHandled = onSpotifyResultHandled,
		)
	}
}

@Composable
private fun AuthOrApp(
	client: SonglyApiClient,
	loggedIn: Boolean?,
	authNotice: String?,
	onAuthNotice: (String?) -> Unit,
	onLoggedInChange: (Boolean) -> Unit,
	onLogoutForLink: () -> Unit,
	authLink: AuthLink?,
	onAuthLinkHandled: () -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
) {
	// Wynik łączenia Spotify bez sesji nie ma już czego dotyczyć — sesja, która
	// zaczęła OAuth, zdążyła się skończyć.
	if (loggedIn == false && spotifyResult != null) {
		LaunchedEffect(spotifyResult) { onSpotifyResultHandled() }
	}
	when (loggedIn) {
		null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}

		true -> {
			LoggedInNav(
				client = client,
				onLoggedOut = { notice -> onAuthNotice(notice); onLoggedInChange(false) },
				spotifyResult = spotifyResult,
				onSpotifyResultHandled = onSpotifyResultHandled,
			)
			if (authLink != null) {
				LinkWhileLoggedInDialog(link = authLink, onLogout = onLogoutForLink, onDismiss = onAuthLinkHandled)
			}
		}

		false -> AuthNav(
			client = client,
			notice = authNotice,
			onNotice = onAuthNotice,
			onAuthenticated = { onAuthNotice(null); onLoggedInChange(true) },
			authLink = authLink,
			onAuthLinkHandled = onAuthLinkHandled,
		)
	}
}

/**
 * Link z maila przy zalogowanej sesji. Oba linki dotyczą konta, do którego trzeba
 * się dopiero dostać (nowego albo z zapomnianym hasłem), więc wymagają wylogowania —
 * ale wylogowanie bez pytania zabrałoby komuś sesję przez przypadkowe kliknięcie
 * w starego maila, a zignorowanie linku wyglądałoby jak awaria.
 */
@Composable
private fun LinkWhileLoggedInDialog(link: AuthLink, onLogout: () -> Unit, onDismiss: () -> Unit) {
	val purpose = when (link) {
		is AuthLink.VerifyEmail -> "potwierdzić adres z tego linku"
		is AuthLink.ResetPassword -> "ustawić nowe hasło z tego linku"
	}
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Otworzyć link z maila?") },
		text = { Text("To urządzenie jest zalogowane do Songly. Żeby $purpose, trzeba się najpierw wylogować.") },
		confirmButton = { TextButton(onClick = onLogout) { Text("Wyloguj się") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
	)
}

private enum class AuthScreen { LOGIN, REGISTER, FORGOT_PASSWORD }

/**
 * Nawigacja przed zalogowaniem: logowanie, rejestracja i reset hasła. Rejestracja
 * i logowanie po sukcesie wywołują [onAuthenticated] (sesja jest już założona w
 * `shared`); reset hasła wraca do logowania, bo backend nie wydaje po nim sesji.
 */
@Composable
private fun AuthNav(
	client: SonglyApiClient,
	notice: String?,
	onNotice: (String?) -> Unit,
	onAuthenticated: () -> Unit,
	authLink: AuthLink?,
	onAuthLinkHandled: () -> Unit,
) {
	var screen by rememberSaveable { mutableStateOf(AuthScreen.LOGIN) }
	// Każde wejście w reset dostaje świeży ViewModel — zcache'owany pamiętałby
	// „hasło ustawione" i od razu wyrzucał z ekranu przy kolejnej próbie.
	var forgotAttempt by rememberSaveable { mutableStateOf(0) }
	val backToLogin = { screen = AuthScreen.LOGIN }

	// Link z maila najpierw przełącza ekran; sam token przekazuje ekranowi docelowemu
	// jego własny efekt niżej, kiedy jego ViewModel już istnieje.
	LaunchedEffect(authLink) {
		when (authLink) {
			is AuthLink.VerifyEmail -> {
				onNotice(null)
				screen = AuthScreen.REGISTER
			}

			is AuthLink.ResetPassword -> {
				onNotice(null)
				// Nowy ViewModel tylko przy wejściu na ekran. Gdyby reset był już otwarty,
				// zmiana klucza w tej samej klatce oddałaby token staremu ViewModelowi,
				// który zaraz potem zostałby podmieniony na pusty.
				if (screen != AuthScreen.FORGOT_PASSWORD) {
					forgotAttempt++
					screen = AuthScreen.FORGOT_PASSWORD
				}
			}

			null -> Unit
		}
	}

	// Wstecz z rejestracji i resetu wraca do logowania, a nie zamyka aplikację.
	BackHandler(enabled = screen != AuthScreen.LOGIN) { backToLogin() }

	when (screen) {
		AuthScreen.REGISTER -> {
			val registerViewModel: RegisterViewModel = viewModel(
				factory = viewModelFactory {
					initializer { RegisterViewModel(client) }
				},
			)
			LaunchedEffect(authLink) {
				if (authLink is AuthLink.VerifyEmail) {
					registerViewModel.openVerificationLink(authLink.token)
					onAuthLinkHandled()
				}
			}
			RegisterScreen(
				viewModel = registerViewModel,
				onRegistered = onAuthenticated,
				onNavigateToLogin = backToLogin,
			)
		}

		AuthScreen.FORGOT_PASSWORD -> {
			val forgotViewModel: ForgotPasswordViewModel = viewModel(
				key = "forgot-password-$forgotAttempt",
				factory = viewModelFactory {
					initializer { ForgotPasswordViewModel(client) }
				},
			)
			LaunchedEffect(authLink) {
				if (authLink is AuthLink.ResetPassword) {
					forgotViewModel.openResetLink(authLink.token)
					onAuthLinkHandled()
				}
			}
			ForgotPasswordScreen(
				viewModel = forgotViewModel,
				onPasswordReset = {
					onNotice("Hasło zostało zmienione. Zaloguj się nowym hasłem.")
					backToLogin()
				},
				onBack = backToLogin,
			)
		}

		AuthScreen.LOGIN -> {
			val loginViewModel: LoginViewModel = viewModel(
				factory = viewModelFactory {
					initializer { LoginViewModel(client) }
				},
			)
			LoginScreen(
				viewModel = loginViewModel,
				onLoggedIn = onAuthenticated,
				onNavigateToRegister = { onNotice(null); screen = AuthScreen.REGISTER },
				onForgotPassword = {
					onNotice(null)
					forgotAttempt++
					screen = AuthScreen.FORGOT_PASSWORD
				},
				notice = notice,
			)
		}
	}
}

/** Jedna fabryka dla obu miejsc, które sięgają po ten ViewModel. */
@Composable
private fun nowPlayingViewModel(client: SonglyApiClient): NowPlayingViewModel = viewModel(
	factory = viewModelFactory {
		initializer { NowPlayingViewModel(client) }
	},
)

/**
 * Nawigacja w obrębie zalogowanej sesji: feed ↔ tworzenie posta. [feedViewModel]
 * jest tu (a nie w gałęzi feedu), żeby po dodaniu posta odświeżyć feed tą samą
 * instancją. Prosty stan zamiast Navigation-Compose — wystarcza na dwa ekrany.
 */
@Composable
private fun LoggedInNav(
	client: SonglyApiClient,
	onLoggedOut: (notice: String?) -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
) {
	val feedViewModel: FeedViewModel = viewModel(
		factory = viewModelFactory {
			initializer { FeedViewModel(client) }
		},
	)
	var creatingPost by rememberSaveable { mutableStateOf(false) }
	var showProfile by rememberSaveable { mutableStateOf(false) }
	var showFriends by rememberSaveable { mutableStateOf(false) }
	var showNowPlaying by rememberSaveable { mutableStateOf(false) }
	var showFriendsPresence by rememberSaveable { mutableStateOf(false) }
	var showNotifications by rememberSaveable { mutableStateOf(false) }
	var showMailPreferences by rememberSaveable { mutableStateOf(false) }
	var showCommunities by rememberSaveable { mutableStateOf(false) }
	var communityDetailId by rememberSaveable { mutableStateOf<String?>(null) }
	var showMatches by rememberSaveable { mutableStateOf(false) }
	var showAdmin by rememberSaveable { mutableStateOf(false) }

	// Powrót z OAuth Spotify: wynik trafia do tego samego ViewModelu, który
	// pokazuje ekran „teraz gram" (viewModel() bez klucza oddaje jedną instancję),
	// a ekran wraca na wierzch, bo proces zostawił go pod przeglądarką.
	if (spotifyResult != null) {
		val nowPlayingViewModel = nowPlayingViewModel(client)
		LaunchedEffect(spotifyResult) {
			nowPlayingViewModel.onSpotifyConnectResult(spotifyResult)
			showNowPlaying = true
			onSpotifyResultHandled()
		}
	}

	// Stan konta sprawdzany na starcie sesji: zawieszone konto dostaje 403 na
	// prawie wszystko (ADR 0022), więc zamiast zepsutego feedu pokazujemy mu jego
	// stan. null = jeszcze sprawdzamy. Brak sieci traktujemy jak „niezawieszone"
	// — feed i tak pokaże wtedy błąd połączenia, a nie fałszywe zawieszenie.
	var suspended by rememberSaveable { mutableStateOf<Boolean?>(null) }
	var isAdmin by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(suspended) {
		if (suspended == null) {
			suspended = runCatching { client.mySuspension().suspended }.getOrDefault(false)
		}
		if (suspended == false) {
			isAdmin = runCatching { client.isCurrentUserAdmin() }.getOrDefault(false)
		}
	}
	when (suspended) {
		null -> {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
			return
		}

		true -> {
			val suspendedViewModel: SuspendedViewModel = viewModel(
				key = "suspended",
				factory = viewModelFactory { initializer { SuspendedViewModel(client) } },
			)
			SuspendedScreen(
				viewModel = suspendedViewModel,
				onLifted = { suspended = false; feedViewModel.refresh() },
				onSessionEnded = { onLoggedOut(null) },
			)
			return
		}

		false -> Unit
	}

	// Profil, post i komentarze to nakładki otwierane z dowolnego ekranu i z siebie
	// nawzajem (profil → post → komentarze → profil komentującego…), więc nie da się
	// ich ułożyć w stałą kolejność gałęzi `when` jak ekranów niżej. Trzymamy je na
	// stosie: rysujemy wierzch, Wstecz zdejmuje jedną warstwę.
	val overlays = rememberSaveable(saver = OverlayStackSaver) { mutableStateListOf<Overlay>() }
	val openUserProfile: (String) -> Unit = { overlays.add(Overlay.UserProfile(it)) }
	val openComments: (String) -> Unit = { overlays.add(Overlay.Comments(it)) }
	val openPost: (String) -> Unit = { overlays.add(Overlay.PostDetail(it)) }
	val closeOverlay: () -> Unit = { overlays.removeAt(overlays.lastIndex) }
	// Zgłoszenie żyje tyle, co jego dialog — świeży stan przy każdym otwarciu.
	val createReportViewModel: (String) -> ReportPostViewModel = { ReportPostViewModel(client, it) }
	val adminViewModel: AdminViewModel = viewModel(
		key = "admin",
		factory = viewModelFactory { initializer { AdminViewModel(client) } },
	)

	// Systemowy Wstecz (przycisk i gest) musi robić to samo co „Wróć" na ekranie —
	// bez tego zamyka aplikację, co na Androidzie jest po prostu zepsute.
	//
	// Kolejność MUSI odpowiadać kolejności gałęzi `when` poniżej: zamykamy ekran,
	// który jest naprawdę na wierzchu, a nie pierwszą podniesioną flagę. Gdy
	// ekranów będzie więcej, ta duplikacja jest sygnałem, że czas na
	// Navigation-Compose z prawdziwym back stackiem.
	val closeTopScreen: (() -> Unit)? = when {
		overlays.isNotEmpty() -> closeOverlay
		showAdmin -> ({ showAdmin = false })
		showMatches -> ({ showMatches = false })
		communityDetailId != null -> ({ communityDetailId = null })
		showCommunities -> ({ showCommunities = false })
		// Otwierane z powiadomień, więc zamyka się przed nimi.
		showMailPreferences -> ({ showMailPreferences = false })
		// Też otwierane z powiadomień (zaproszenie do znajomych).
		showFriends -> ({ showFriends = false })
		// Powrót z powiadomień odświeża feed tak samo jak „Wróć" — inaczej badge
		// nieprzeczytanych zostawałby po lekturze.
		showNotifications -> ({ showNotifications = false; feedViewModel.refresh() })
		showFriendsPresence -> ({ showFriendsPresence = false })
		showNowPlaying -> ({ showNowPlaying = false })
		showProfile -> ({ showProfile = false })
		creatingPost -> ({ creatingPost = false })
		// Na feedzie zostawiamy Wstecz systemowi — tam wyjście z aplikacji jest
		// zachowaniem oczekiwanym.
		else -> null
	}
	BackHandler(enabled = closeTopScreen != null) { closeTopScreen?.invoke() }

	val topOverlay = overlays.lastOrNull()
	when {
		// Klucze ViewModeli per zasób — inaczej viewModel() oddałby dane innej
		// osoby/posta. Klucz zawiera też głębokość stosu: ten sam profil otwarty
		// dwa razy (profil → komentarze → ten sam profil) dostaje świeży stan.
		topOverlay is Overlay.UserProfile -> {
			val key = "${overlays.lastIndex}-${topOverlay.userId}"
			val userProfileViewModel: UserProfileViewModel = viewModel(
				key = "user-profile-$key",
				factory = viewModelFactory {
					initializer { UserProfileViewModel(client, topOverlay.userId) }
				},
			)
			val postsViewModel: PostListViewModel = viewModel(
				key = "user-posts-$key",
				factory = viewModelFactory {
					initializer {
						PostListViewModel(client) { page, size -> postsByAuthor(topOverlay.userId, page, size) }
					}
				},
			)
			UserProfileScreen(
				viewModel = userProfileViewModel,
				postsViewModel = postsViewModel,
				onBack = closeOverlay,
				onOpenComments = openComments,
				isAdminViewer = isAdmin,
				onOpenModerationHistory = { userId ->
					overlays.clear()
					showAdmin = true
					adminViewModel.showLogFor(userId)
				},
			)
		}

		topOverlay is Overlay.PostDetail -> {
			val postViewModel: PostDetailViewModel = viewModel(
				key = "post-${overlays.lastIndex}-${topOverlay.postId}",
				factory = viewModelFactory {
					initializer { PostDetailViewModel(client, topOverlay.postId) }
				},
			)
			PostDetailScreen(
				viewModel = postViewModel,
				onBack = closeOverlay,
				onOpenComments = openComments,
				onOpenUserProfile = openUserProfile,
				createReportViewModel = createReportViewModel,
			)
		}

		topOverlay is Overlay.Comments -> {
			val postId = topOverlay.postId
			val commentsViewModel: CommentsViewModel = viewModel(
				key = "comments-${overlays.lastIndex}-$postId",
				factory = viewModelFactory {
					initializer { CommentsViewModel(client, postId) }
				},
			)
			CommentsScreen(
				viewModel = commentsViewModel,
				onBack = closeOverlay,
				onCommentAdded = { feedViewModel.incrementCommentCount(postId) },
				onCommentDeleted = { feedViewModel.decrementCommentCount(postId) },
				onOpenUserProfile = openUserProfile,
			)
		}

		showAdmin -> AdminScreen(
			viewModel = adminViewModel,
			onBack = { showAdmin = false },
			onOpenUserProfile = openUserProfile,
			onOpenCommunity = { showAdmin = false; communityDetailId = it },
		)

		showMatches -> {
			val matchesViewModel: MatchesViewModel = viewModel(
				factory = viewModelFactory {
					initializer { MatchesViewModel(client) }
				},
			)
			MatchesScreen(
				viewModel = matchesViewModel,
				onBack = { showMatches = false },
				onOpenUserProfile = openUserProfile,
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
			val communityPostsViewModel: PostListViewModel = viewModel(
				key = "community-posts-$id",
				factory = viewModelFactory {
					initializer { PostListViewModel(client) { page, size -> communityPosts(id, page, size) } }
				},
			)
			val moderationViewModel: CommunityModerationViewModel = viewModel(
				key = "community-moderation-$id",
				factory = viewModelFactory {
					initializer { CommunityModerationViewModel(client, id) }
				},
			)
			CommunityDetailScreen(
				viewModel = detailViewModel,
				postsViewModel = communityPostsViewModel,
				onOpenComments = openComments,
				onBack = { communityDetailId = null },
				// Po usunięciu wróć do listy — CommunitiesScreen odświeży się sam przy wejściu.
				onDeleted = { communityDetailId = null },
				onOpenUserProfile = openUserProfile,
				moderationViewModel = moderationViewModel,
				createReportViewModel = createReportViewModel,
				onOpenPost = openPost,
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
				onOpenUserProfile = openUserProfile,
			)
		}

		showMailPreferences -> {
			val mailPreferencesViewModel: MailPreferencesViewModel = viewModel(
				factory = viewModelFactory {
					initializer { MailPreferencesViewModel(client) }
				},
			)
			MailPreferencesScreen(
				viewModel = mailPreferencesViewModel,
				onBack = { showMailPreferences = false },
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
				onOpenUserProfile = openUserProfile,
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
				onOpenUserProfile = openUserProfile,
				onOpenPost = openPost,
				onOpenCommunity = { communityDetailId = it },
				onOpenFriends = { showFriends = true },
				onOpenMailPreferences = { showMailPreferences = true },
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
				onOpenUserProfile = openUserProfile,
			)
		}

		showNowPlaying -> {
			val nowPlayingViewModel = nowPlayingViewModel(client)
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
				onAccountDeleted = { showProfile = false; onLoggedOut(null) },
				onPasswordChanged = {
					showProfile = false
					onLoggedOut("Hasło zostało zmienione, a wszystkie sesje zakończone. Zaloguj się nowym hasłem.")
				},
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

		else -> {
			FeedScreen(
				viewModel = feedViewModel,
				client = client,
				onLoggedOut = { onLoggedOut(null) },
				onSessionExpired = { onLoggedOut("Sesja wygasła. Zaloguj się ponownie.") },
				onCreatePost = { creatingPost = true },
				onOpenComments = openComments,
				onOpenUserProfile = openUserProfile,
				onOpenProfile = { showProfile = true },
				onOpenFriends = { showFriends = true },
				onOpenNowPlaying = { showNowPlaying = true },
				onOpenFriendsPresence = { showFriendsPresence = true },
				onOpenNotifications = { showNotifications = true },
				onOpenCommunities = { showCommunities = true },
				onOpenMatches = { showMatches = true },
				isAdmin = isAdmin,
				onOpenAdmin = { showAdmin = true },
				// Zawieszenie w trakcie sesji — sprawdzamy stan od nowa i pokazujemy ekran zawieszenia.
				onAccountSuspended = { suspended = null },
			)
		}
	}
}

/** Nakładki otwierane z dowolnego ekranu — patrz stos w [LoggedInNav]. */
private sealed interface Overlay {
	data class UserProfile(val userId: String) : Overlay
	data class PostDetail(val postId: String) : Overlay
	data class Comments(val postId: String) : Overlay
}

/**
 * Stos nakładek zapisany jako lista napisów `rodzaj:id` — `Bundle` nie przyjmie
 * zwykłej klasy Kotlina, a sam identyfikator wystarcza, żeby odtworzyć warstwę
 * (dane i tak trzyma ViewModel, który przeżywa obrót).
 */
private val OverlayStackSaver = listSaver<SnapshotStateList<Overlay>, String>(
	save = { stack ->
		stack.map {
			when (it) {
				is Overlay.UserProfile -> "profile:${it.userId}"
				is Overlay.PostDetail -> "post:${it.postId}"
				is Overlay.Comments -> "comments:${it.postId}"
			}
		}
	},
	restore = { saved ->
		saved.mapNotNullTo(mutableStateListOf()) {
			val id = it.substringAfter(':')
			when (it.substringBefore(':')) {
				"profile" -> Overlay.UserProfile(id)
				"post" -> Overlay.PostDetail(id)
				"comments" -> Overlay.Comments(id)
				else -> null
			}
		}
	},
)
