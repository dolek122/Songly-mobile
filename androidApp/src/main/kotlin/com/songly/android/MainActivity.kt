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
import androidx.compose.material3.MaterialTheme
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

class MainActivity : ComponentActivity() {

	private var pendingAuthLink by mutableStateOf<AuthLink?>(null)

	private var pendingSpotifyResult by mutableStateOf<SpotifyConnectResult?>(null)

	private var pendingOpenNotifications by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		setTheme(R.style.Theme_Songly)
		enableEdgeToEdge(
			statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
			navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
		)
		super.onCreate(savedInstanceState)
		if (savedInstanceState == null) takeLink(intent)
		val app = application as SonglyApplication
		setContent {
			SonglyTheme {
				Surface(
					modifier = Modifier.fillMaxSize().songlyBackdrop().imePadding(),
					color = androidx.compose.ui.graphics.Color.Transparent,
					contentColor = MaterialTheme.colorScheme.onBackground,
				) {
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
	var loggedIn by rememberSaveable { mutableStateOf<Boolean?>(null) }
	var authNotice by rememberSaveable { mutableStateOf<String?>(null) }
	val scope = rememberCoroutineScope()

	LaunchedEffect(Unit) {
		loggedIn = client.isLoggedIn()
	}

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

		false -> Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
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
	var forgotAttempt by rememberSaveable { mutableStateOf(0) }
	val backToLogin = { screen = AuthScreen.LOGIN }

	LaunchedEffect(authLink) {
		when (authLink) {
			is AuthLink.VerifyEmail -> {
				onNotice(null)
				screen = AuthScreen.REGISTER
			}

			is AuthLink.ResetPassword -> {
				onNotice(null)
				if (screen != AuthScreen.FORGOT_PASSWORD) {
					forgotAttempt++
					screen = AuthScreen.FORGOT_PASSWORD
				}
			}

			null -> Unit
		}
	}

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

@Composable
private fun nowPlayingViewModel(client: SonglyApiClient): NowPlayingViewModel = viewModel(
	factory = viewModelFactory {
		initializer { NowPlayingViewModel(client) }
	},
)

@Composable
private fun profileViewModel(client: SonglyApiClient): ProfileViewModel = viewModel(
	factory = viewModelFactory {
		initializer { ProfileViewModel(client) }
	},
)

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
	var showProfile by rememberSaveable { mutableStateOf(false) }
	var showFriends by rememberSaveable { mutableStateOf(false) }
	var showFriendsPresence by rememberSaveable { mutableStateOf(false) }
	var showNotifications by rememberSaveable { mutableStateOf(false) }
	var showMailPreferences by rememberSaveable { mutableStateOf(false) }
	var communityDetailId by rememberSaveable { mutableStateOf<String?>(null) }
	var showMatches by rememberSaveable { mutableStateOf(false) }
	var showAdmin by rememberSaveable { mutableStateOf(false) }

	if (spotifyResult != null) {
		val nowPlayingViewModel = nowPlayingViewModel(client)
		LaunchedEffect(spotifyResult) {
			nowPlayingViewModel.onSpotifyConnectResult(spotifyResult)
			tab = MainTab.MUSIC
			onSpotifyResultHandled()
		}
	}

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

	var suspended by rememberSaveable { mutableStateOf<Boolean?>(null) }
	var isAdmin by rememberSaveable { mutableStateOf(false) }
	var showOnboarding by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(suspended) {
		if (suspended == null) {
			val isSuspended = runCatching { client.mySuspension().suspended }.getOrDefault(false)
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

	if (showOnboarding) {
		val onboardingViewModel: GenreOnboardingViewModel = viewModel(
			key = "genre-onboarding",
			factory = viewModelFactory { initializer { GenreOnboardingViewModel(client, onboardingStore) } },
		)
		GenreOnboardingScreen(
			viewModel = onboardingViewModel,
			onFinished = { saved ->
				showOnboarding = false
				if (saved) showMatches = true
			},
		)
		return
	}

	val overlays = rememberSaveable(saver = OverlayStackSaver) { mutableStateListOf<Overlay>() }

	val screenStates = rememberSaveableStateHolder()
	val tabStates = rememberSaveableStateHolder()

	val openUserProfile: (String) -> Unit = { overlays.add(Overlay.UserProfile(it)) }
	val openComments: (String) -> Unit = { overlays.add(Overlay.Comments(it)) }
	val openPost: (String) -> Unit = { overlays.add(Overlay.PostDetail(it)) }
	val closeOverlay: () -> Unit = {
		screenStates.removeState(overlayStateKey(overlays.lastIndex, overlays.last()))
		overlays.removeAt(overlays.lastIndex)
	}
	val createReportViewModel: (String) -> ReportPostViewModel = { ReportPostViewModel(client, it) }
	val adminViewModel: AdminViewModel = viewModel(
		key = "admin",
		factory = viewModelFactory { initializer { AdminViewModel(client) } },
	)

	val closeTopScreen: (() -> Unit)? = when {
		overlays.isNotEmpty() -> closeOverlay
		showAdmin -> ({ showAdmin = false })
		showMatches -> ({ showMatches = false })
		communityDetailId != null -> ({ communityDetailId = null })
		showMailPreferences -> ({ showMailPreferences = false })
		showFriends -> ({ showFriends = false })
		showNotifications -> ({ showNotifications = false; feedViewModel.refresh() })
		showFriendsPresence -> ({ showFriendsPresence = false })
		showProfile -> ({ showProfile = false })
		creatingPost -> ({ creatingPost = false })
		tab != MainTab.HOME -> ({ tab = MainTab.HOME })
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

private fun overlayStateKey(index: Int, overlay: Overlay): String = "overlay-$index-$overlay"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs(selected: MainTab, onSelect: (MainTab) -> Unit, content: @Composable () -> Unit) {
	Scaffold(
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

private sealed interface Overlay {
	data class UserProfile(val userId: String) : Overlay
	data class PostDetail(val postId: String) : Overlay
	data class Comments(val postId: String) : Overlay
}

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
