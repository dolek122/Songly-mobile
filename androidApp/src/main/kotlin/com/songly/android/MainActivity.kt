package com.songly.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.songly.android.onboarding.GenreOnboardingScreen
import com.songly.android.onboarding.GenreOnboardingViewModel
import com.songly.android.onboarding.OnboardingStore
import com.songly.android.onboarding.needsGenreOnboarding
import com.songly.android.presence.FriendsPresenceScreen
import com.songly.android.presence.FriendsPresenceViewModel
import com.songly.android.post.CreatePostScreen
import com.songly.android.post.CreatePostViewModel
import com.songly.android.post.PostDetailScreen
import com.songly.android.post.PostDetailViewModel
import com.songly.android.post.PostListViewModel
import com.songly.android.profile.ProfileHubScreen
import com.songly.android.profile.ProfileScreen
import com.songly.android.profile.ProfileViewModel
import com.songly.android.profile.UserProfileScreen
import com.songly.android.profile.UserProfileViewModel
import com.songly.android.push.PushSetup
import com.songly.android.ui.MainTab
import com.songly.android.ui.SonglyNavigationBar
import com.songly.android.ui.songlyBackdrop
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
 *
 * Aplikacja rysuje się od krawędzi do krawędzi na każdej wersji Androida, nie
 * tylko na 15+, gdzie system to wymusza — inaczej ten sam ekran wyglądałby inaczej
 * na różnych telefonach, a klawiatura na Androidzie 15 zasłaniała pola tekstowe
 * (tam `adjustResize` już niczego nie zmniejsza, odstęp liczy sam Compose).
 */
class MainActivity : ComponentActivity() {

	// Link z maila czekający na obsłużenie (ADR 0037 backendu). Trzymany w Activity,
	// nie w kompozycji, bo przy otwartej aplikacji przychodzi przez onNewIntent.
	private var pendingAuthLink by mutableStateOf<AuthLink?>(null)

	// Wynik OAuth Spotify z linku powrotnego (ADR 0038 backendu), z tych samych
	// powodów trzymany w Activity co link z maila.
	private var pendingSpotifyResult by mutableStateOf<SpotifyConnectResult?>(null)

	// Stuknięcie w push (ADR 0040 backendu) — otwiera skrzynkę powiadomień.
	private var pendingOpenNotifications by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		// Manifest daje oknu motyw z logo w tle, żeby start na Androidzie 8–11 (bez
		// systemowego ekranu powitalnego) nie był pustym czarnym prostokątem. Zanim
		// cokolwiek narysujemy, wracamy do zwykłego motywu — inaczej logo
		// prześwitywałoby zza każdego przezroczystego ekranu.
		setTheme(R.style.Theme_Songly)
		// Ciemne paski z jasnymi ikonami niezależnie od trybu systemu: aplikacja jest
		// zawsze ciemna (SonglyPalette), a wariant „auto" dałby czarne ikony na czerni.
		enableEdgeToEdge(
			statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
			navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
		)
		super.onCreate(savedInstanceState)
		// Po obrocie albo odtworzeniu procesu Activity dostaje ten sam intent jeszcze
		// raz — obsłużony już link nie może potwierdzać adresu drugi raz.
		if (savedInstanceState == null) takeLink(intent)
		val app = application as SonglyApplication
		setContent {
			SonglyTheme {
				// imePadding u samej góry drzewa: klawiatura zabiera miejsce wszystkim
				// ekranom naraz, a Scaffoldy niżej widzą wcięcie paska nawigacji jako już
				// zużyte (nie dokładają drugiego odstępu nad klawiaturą).
				Surface(modifier = Modifier.fillMaxSize().imePadding()) {
					AppRoot(
						app.apiClient,
						onboardingStore = app.onboardingStore,
						authLink = pendingAuthLink,
						onAuthLinkHandled = { pendingAuthLink = null },
						spotifyResult = pendingSpotifyResult,
						onSpotifyResultHandled = { pendingSpotifyResult = null },
						openNotifications = pendingOpenNotifications,
						onOpenNotificationsHandled = { pendingOpenNotifications = false },
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
		if (intent?.action == ACTION_OPEN_NOTIFICATIONS) {
			pendingOpenNotifications = true
			return
		}
		val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
		AuthLink.parse(url)?.let { pendingAuthLink = it }
		SpotifyConnectResult.parse(url)?.let { pendingSpotifyResult = it }
	}

	companion object {
		/** Akcja intencji z pusha — nie jest w manifeście, bo wysyła ją wyłącznie nasz PendingIntent. */
		const val ACTION_OPEN_NOTIFICATIONS = "com.songly.android.OPEN_NOTIFICATIONS"
	}
}

@Composable
private fun AppRoot(
	client: SonglyApiClient,
	onboardingStore: OnboardingStore,
	authLink: AuthLink?,
	onAuthLinkHandled: () -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
	openNotifications: Boolean,
	onOpenNotificationsHandled: () -> Unit,
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
			onboardingStore = onboardingStore,
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
			openNotifications = openNotifications,
			onOpenNotificationsHandled = onOpenNotificationsHandled,
		)
	}
}

@Composable
private fun AuthOrApp(
	client: SonglyApiClient,
	onboardingStore: OnboardingStore,
	loggedIn: Boolean?,
	authNotice: String?,
	onAuthNotice: (String?) -> Unit,
	onLoggedInChange: (Boolean) -> Unit,
	onLogoutForLink: () -> Unit,
	authLink: AuthLink?,
	onAuthLinkHandled: () -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
	openNotifications: Boolean,
	onOpenNotificationsHandled: () -> Unit,
) {
	// Wynik łączenia Spotify bez sesji nie ma już czego dotyczyć — sesja, która
	// zaczęła OAuth, zdążyła się skończyć. To samo ze stuknięciem w push.
	if (loggedIn == false && spotifyResult != null) {
		LaunchedEffect(spotifyResult) { onSpotifyResultHandled() }
	}
	if (loggedIn == false && openNotifications) {
		LaunchedEffect(Unit) { onOpenNotificationsHandled() }
	}
	when (loggedIn) {
		null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}

		true -> {
			LoggedInNav(
				client = client,
				onboardingStore = onboardingStore,
				onLoggedOut = { notice -> onAuthNotice(notice); onLoggedInChange(false) },
				spotifyResult = spotifyResult,
				onSpotifyResultHandled = onSpotifyResultHandled,
				openNotifications = openNotifications,
				onOpenNotificationsHandled = onOpenNotificationsHandled,
			)
			if (authLink != null) {
				LinkWhileLoggedInDialog(link = authLink, onLogout = onLogoutForLink, onDismiss = onAuthLinkHandled)
			}
		}

		// Ekrany logowania nie mają Scaffolda, więc odstęp od pasków systemu dają sobie
		// same; poświata rysuje się pod paskiem stanu.
		false -> Box(modifier = Modifier.fillMaxSize().songlyBackdrop().systemBarsPadding()) {
			AuthNav(
				client = client,
				notice = authNotice,
				onNotice = onAuthNotice,
				onAuthenticated = { onAuthNotice(null); onLoggedInChange(true) },
				authLink = authLink,
				onAuthLinkHandled = onAuthLinkHandled,
			)
		}
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

/** Jeden ViewModel profilu dla zakładki „Profil" i ekranu edycji — ten sam stan. */
@Composable
private fun profileViewModel(client: SonglyApiClient): ProfileViewModel = viewModel(
	factory = viewModelFactory {
		initializer { ProfileViewModel(client) }
	},
)

/**
 * Nawigacja w obrębie zalogowanej sesji. Na dole pięć zakładek ([MainTab]) z
 * głównymi miejscami aplikacji; pozostałe ekrany (powiadomienia, szczegóły,
 * edycja) otwierają się nad zakładkami na pełnym ekranie, ze strzałką wstecz.
 * [feedViewModel] jest tu (a nie w gałęzi feedu), żeby po dodaniu posta odświeżyć
 * feed tą samą instancją. Prosty stan zamiast Navigation-Compose.
 */
@Composable
private fun LoggedInNav(
	client: SonglyApiClient,
	onboardingStore: OnboardingStore,
	onLoggedOut: (notice: String?) -> Unit,
	spotifyResult: SpotifyConnectResult?,
	onSpotifyResultHandled: () -> Unit,
	openNotifications: Boolean,
	onOpenNotificationsHandled: () -> Unit,
) {
	val feedViewModel: FeedViewModel = viewModel(
		factory = viewModelFactory {
			initializer { FeedViewModel(client) }
		},
	)
	val scope = rememberCoroutineScope()
	var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
	var creatingPost by rememberSaveable { mutableStateOf(false) }
	// Edycja profilu — otwierana z zakładki „Profil".
	var showProfile by rememberSaveable { mutableStateOf(false) }
	// Znajomi nad innym ekranem (zaproszenie z powiadomień); jako zakładka to [tab].
	var showFriends by rememberSaveable { mutableStateOf(false) }
	var showFriendsPresence by rememberSaveable { mutableStateOf(false) }
	var showNotifications by rememberSaveable { mutableStateOf(false) }
	var showMailPreferences by rememberSaveable { mutableStateOf(false) }
	var communityDetailId by rememberSaveable { mutableStateOf<String?>(null) }
	var showMatches by rememberSaveable { mutableStateOf(false) }
	var showAdmin by rememberSaveable { mutableStateOf(false) }

	// Powrót z OAuth Spotify: wynik trafia do tego samego ViewModelu, który
	// pokazuje zakładkę „Muzyka" (viewModel() bez klucza oddaje jedną instancję),
	// a zakładka wraca na wierzch, bo proces zostawił ją pod przeglądarką.
	if (spotifyResult != null) {
		val nowPlayingViewModel = nowPlayingViewModel(client)
		LaunchedEffect(spotifyResult) {
			nowPlayingViewModel.onSpotifyConnectResult(spotifyResult)
			tab = MainTab.MUSIC
			onSpotifyResultHandled()
		}
	}

	// Stuknięcie w push: skrzynka na wierzch. Ekrany, które w `when` niżej stoją
	// przed nią, zamykamy — inaczej skrzynka otworzyłaby się pod nimi, niewidoczna.
	if (openNotifications) {
		LaunchedEffect(Unit) {
			showAdmin = false
			showMatches = false
			communityDetailId = null
			showMailPreferences = false
			showFriends = false
			showNotifications = true
			onOpenNotificationsHandled()
		}
	}

	PushSetup(client)

	// Stan konta sprawdzany na starcie sesji: zawieszone konto dostaje 403 na
	// prawie wszystko (ADR 0022), więc zamiast zepsutego feedu pokazujemy mu jego
	// stan. null = jeszcze sprawdzamy. Brak sieci traktujemy jak „niezawieszone"
	// — feed i tak pokaże wtedy błąd połączenia, a nie fałszywe zawieszenie.
	var suspended by rememberSaveable { mutableStateOf<Boolean?>(null) }
	var isAdmin by rememberSaveable { mutableStateOf(false) }
	// Onboarding z gatunkami dla konta, które ich nie ma (patrz GenreOnboardingViewModel).
	var showOnboarding by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(suspended) {
		if (suspended == null) {
			val isSuspended = runCatching { client.mySuspension().suspended }.getOrDefault(false)
			// Jeszcze pod spinnerem startowym, żeby nowe konto nie mignęło pustym feedem.
			// Zwykle bez dodatkowego żądania: po pierwszym razie decyduje flaga w pamięci
			// urządzenia, a id konta i tak pobiera feed. Błąd = bez onboardingu (wróci
			// przy następnym starcie), bo blokowanie aplikacji przez niego byłoby gorsze.
			if (!isSuspended) {
				showOnboarding = runCatching { needsGenreOnboarding(client, onboardingStore) }.getOrDefault(false)
			}
			suspended = isSuspended
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
			Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
				SuspendedScreen(
					viewModel = suspendedViewModel,
					onLifted = { suspended = false; feedViewModel.refresh() },
					onSessionEnded = { onLoggedOut(null) },
				)
			}
			return
		}

		false -> Unit
	}

	// Onboarding zasłania całą aplikację, jak ekran zawieszenia: to pierwsza rzecz po
	// zalogowaniu i nie ma pod nim czego pokazywać. Bez własnej obsługi Wstecz —
	// systemowy Wstecz wychodzi z aplikacji, a pytanie wraca przy następnym starcie.
	if (showOnboarding) {
		val onboardingViewModel: GenreOnboardingViewModel = viewModel(
			key = "genre-onboarding",
			factory = viewModelFactory { initializer { GenreOnboardingViewModel(client, onboardingStore) } },
		)
		GenreOnboardingScreen(
			viewModel = onboardingViewModel,
			onFinished = { saved ->
				showOnboarding = false
				// Po zapisie od razu dopasowani — to obietnica z ekranu onboardingu.
				if (saved) showMatches = true
			},
		)
		return
	}

	// Profil, post i komentarze to nakładki otwierane z dowolnego ekranu i z siebie
	// nawzajem (profil → post → komentarze → profil komentującego…), więc nie da się
	// ich ułożyć w stałą kolejność gałęzi `when` jak ekranów niżej. Trzymamy je na
	// stosie: rysujemy wierzch, Wstecz zdejmuje jedną warstwę.
	val overlays = rememberSaveable(saver = OverlayStackSaver) { mutableStateListOf<Overlay>() }

	// Stan zapisywalny (pozycja na liście, wpisany tekst) każdego ekranu z osobna.
	// Bez tego wejście w komentarze i powrót przewijały feed na sam początek, bo
	// zakładki znikały z kompozycji razem z całym swoim `rememberSaveable`.
	val screenStates = rememberSaveableStateHolder()
	val tabStates = rememberSaveableStateHolder()

	val openUserProfile: (String) -> Unit = { overlays.add(Overlay.UserProfile(it)) }
	val openComments: (String) -> Unit = { overlays.add(Overlay.Comments(it)) }
	val openPost: (String) -> Unit = { overlays.add(Overlay.PostDetail(it)) }
	val closeOverlay: () -> Unit = {
		// Zdjęta warstwa nie wróci — jej stan nie ma po co czekać w pamięci.
		screenStates.removeState(overlayStateKey(overlays.lastIndex, overlays.last()))
		overlays.removeAt(overlays.lastIndex)
	}
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
		// Otwierane z powiadomień, więc zamyka się przed nimi.
		showMailPreferences -> ({ showMailPreferences = false })
		// Też otwierane z powiadomień (zaproszenie do znajomych).
		showFriends -> ({ showFriends = false })
		// Powrót z powiadomień odświeża feed tak samo jak „Wróć" — inaczej badge
		// nieprzeczytanych zostawałby po lekturze.
		showNotifications -> ({ showNotifications = false; feedViewModel.refresh() })
		showFriendsPresence -> ({ showFriendsPresence = false })
		showProfile -> ({ showProfile = false })
		creatingPost -> ({ creatingPost = false })
		// Z innej zakładki Wstecz wraca na główną, jak w większości aplikacji z
		// dolnym paskiem — dopiero z głównej wychodzi z aplikacji.
		tab != MainTab.HOME -> ({ tab = MainTab.HOME })
		// Na głównej zostawiamy Wstecz systemowi — tam wyjście z aplikacji jest
		// zachowaniem oczekiwanym.
		else -> null
	}
	BackHandler(enabled = closeTopScreen != null) { closeTopScreen?.invoke() }

	val topOverlay = overlays.lastOrNull()
	val screenKey = when {
		topOverlay != null -> overlayStateKey(overlays.lastIndex, topOverlay)
		showAdmin -> "admin"
		showMatches -> "matches"
		communityDetailId != null -> "community-$communityDetailId"
		showMailPreferences -> "mail-preferences"
		showFriends -> "friends"
		showNotifications -> "notifications"
		showFriendsPresence -> "friends-presence"
		showProfile -> "profile"
		creatingPost -> "create-post"
		else -> "tabs"
	}
	screenStates.SaveableStateProvider(screenKey) {
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
					onOpenMatches = { showMatches = true },
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

			showProfile -> {
				ProfileScreen(
					viewModel = profileViewModel(client),
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
						feedViewModel.refresh(scrollToTop = true)
					},
				)
			}

			else -> MainTabs(selected = tab, onSelect = { tab = it }) {
				tabStates.SaveableStateProvider(tab.name) {
					when (tab) {
						MainTab.HOME -> FeedScreen(
							viewModel = feedViewModel,
							onSessionExpired = { onLoggedOut("Sesja wygasła. Zaloguj się ponownie.") },
							onCreatePost = { creatingPost = true },
							onOpenComments = openComments,
							onOpenUserProfile = openUserProfile,
							onOpenNotifications = { showNotifications = true },
							// Zawieszenie w trakcie sesji — sprawdzamy stan od nowa i pokazujemy ekran zawieszenia.
							onAccountSuspended = { suspended = null },
							onOpenFriendsPresence = { showFriendsPresence = true },
						)

						MainTab.MUSIC -> NowPlayingScreen(
							viewModel = nowPlayingViewModel(client),
							onOpenFriendsPresence = { showFriendsPresence = true },
						)

						MainTab.COMMUNITIES -> {
							val communitiesViewModel: CommunitiesViewModel = viewModel(
								factory = viewModelFactory {
									initializer { CommunitiesViewModel(client) }
								},
							)
							CommunitiesScreen(
								viewModel = communitiesViewModel,
								onOpenCommunity = { communityDetailId = it },
								onOpenUserProfile = openUserProfile,
							)
						}

						MainTab.FRIENDS -> {
							val friendsViewModel: FriendsViewModel = viewModel(
								factory = viewModelFactory {
									initializer { FriendsViewModel(client) }
								},
							)
							FriendsScreen(
								viewModel = friendsViewModel,
								onOpenUserProfile = openUserProfile,
								onOpenMatches = { showMatches = true },
							)
						}

						MainTab.PROFILE -> ProfileHubScreen(
							viewModel = profileViewModel(client),
							isAdmin = isAdmin,
							onEditProfile = { showProfile = true },
							onOpenMailPreferences = { showMailPreferences = true },
							onOpenAdmin = { showAdmin = true },
							onLogout = {
								scope.launch {
									client.logout()
									onLoggedOut(null)
								}
							},
						)
					}
				}
			}
		}
	}
}

/** Klucz stanu warstwy — z głębokością stosu, jak klucze ViewModeli niżej. */
private fun overlayStateKey(index: Int, overlay: Overlay): String = "overlay-$index-$overlay"

/**
 * Zakładka z dolnym paskiem. Ekrany w środku mają własne Scaffoldy (pasek u góry,
 * FAB), więc pasek nawigacji systemu, który zasłania już nasz dolny pasek, trzeba
 * oznaczyć jako zużyty — inaczej ekran zostawiłby nad paskiem drugi, pusty odstęp
 * tej samej wysokości (aplikacja rysuje się pod paskami systemu).
 *
 * Za zakładkami jest wspólna zielona poświata ([songlyBackdrop]), a ich Scaffoldy
 * są przezroczyste. Przy otwartej klawiaturze dolny pasek się chowa: nad
 * klawiaturą zabierałby miejsce polu, w którym właśnie się pisze.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs(selected: MainTab, onSelect: (MainTab) -> Unit, content: @Composable () -> Unit) {
	Scaffold(
		modifier = Modifier.songlyBackdrop(),
		containerColor = androidx.compose.ui.graphics.Color.Transparent,
		bottomBar = {
			if (!WindowInsets.isImeVisible) SonglyNavigationBar(selected = selected, onSelect = onSelect)
		},
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
	) { padding ->
		Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
			content()
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
