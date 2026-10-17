package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.ApiError
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.Comment
import com.songly.shared.model.ConnectResponse
import com.songly.shared.model.CreateCommentRequest
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.CreateCommunityRequest
import com.songly.shared.model.CreatePostRequest
import com.songly.shared.model.UpdateCommunityRequest
import com.songly.shared.model.DeleteAccountRequest
import com.songly.shared.model.ManualNowPlayingRequest
import com.songly.shared.model.Notification
import com.songly.shared.model.NowPlayingSettings
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.model.LoginRequest
import com.songly.shared.model.Match
import com.songly.shared.model.MembershipStatus
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.model.FriendRequest
import com.songly.shared.model.Genre
import com.songly.shared.model.Page
import com.songly.shared.model.Post
import com.songly.shared.model.Profile
import com.songly.shared.model.PublicProfile
import com.songly.shared.model.RefreshRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.SendFriendRequestRequest
import com.songly.shared.model.SetFavoriteGenresRequest
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.UnreadCount
import com.songly.shared.model.UpdateNowPlayingSettingsRequest
import com.songly.shared.model.UpdateProfileRequest
import com.songly.shared.model.UserSearchResult
import com.songly.shared.model.VerifyEmailRequest
import com.songly.shared.presence.FriendsPresence
import com.songly.shared.presence.PresenceProblem
import com.songly.shared.presence.applyPresenceChange
import com.songly.shared.presence.presenceProblemFor
import com.songly.shared.presence.reconnectDelayMillis
import com.songly.shared.session.InMemoryTokenStorage
import com.songly.shared.session.TokenStorage
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

/**
 * Jedyny punkt styku mobilki z REST API Songly. Trzyma warstwę sieciową i sesji
 * w `shared` (KMP, §14 blueprintu backendu), żeby Android i iOS dzieliły dokładnie
 * tę samą logikę wywołań, mapowania błędów i odświeżania tokenów.
 *
 * Sesja: po [verifyRegistration]/[login] tokeny lądują w [storage]; przy każdym żądaniu
 * uwierzytelnionym access token dokłada wtyczka Auth, a po 401 sama odświeża go
 * refresh tokenem (patrz [createHttpClient]). UI nie dotyka tokenów — woła metody
 * domenowe i ewentualnie łapie [SonglyApiException].
 */
class SonglyApiClient internal constructor(
	config: ApiConfig,
	private val storage: TokenStorage,
	engine: HttpClientEngine?,
) {

	constructor(config: ApiConfig, storage: TokenStorage = InMemoryTokenStorage()) : this(config, storage, null)

	companion object {

		/**
		 * Klient na podstawionym silniku HTTP — **wyłącznie do testów**.
		 *
		 * Osobna fabryka zamiast domyślnego argumentu w konstruktorze, bo typ silnika
		 * pochodzi z Ktora: gdyby wisiał w głównym konstruktorze, każdy moduł tworzący
		 * klienta (czyli aplikacje) musiałby mieć Ktora na swojej ścieżce kompilacji
		 * tylko po to, żeby wywołać konstruktor z domyślnymi argumentami.
		 */
		fun withEngine(config: ApiConfig, storage: TokenStorage, engine: HttpClientEngine): SonglyApiClient =
			SonglyApiClient(config, storage, engine)
	}

	private val http = createHttpClient(config, storage, engine)
	private val base = config.baseUrl.trimEnd('/')

	// Ramki SSE to czysty tekst poza ContentNegotiation, więc strumień presence
	// potrzebuje własnego parsera — z tą samą tolerancją na nieznane pola.
	private val presenceJson = Json { ignoreUnknownKeys = true; isLenient = true }

	// Id zalogowanego użytkownika — cache, żeby UI mógł tanio rozpoznać własne
	// treści (np. pokazać usuwanie tylko autorowi). Czyszczone przy końcu sesji.
	private var cachedUserId: String? = null

	/** Czy istnieje zapisana sesja (są tokeny). Nie gwarantuje ich ważności. */
	suspend fun isLoggedIn(): Boolean = storage.getTokens() != null

	/**
	 * Id zalogowanego użytkownika (z profilu `/me`). Pobierane raz i cache'owane —
	 * UI używa go do rozpoznania własnych postów/komentarzy. Rzuca [SonglyApiException]
	 * jak każde inne żądanie, gdy sesja jest nieważna.
	 */
	suspend fun currentUserId(): String =
		cachedUserId ?: myProfile().userId.also { cachedUserId = it }

	/**
	 * Zgłasza rejestrację (backend ADR 0009). **Nie zakłada konta ani sesji** —
	 * serwer odpowiada 202 bez tokenów i wysyła link potwierdzający na podany
	 * adres; konto powstaje dopiero w [verifyRegistration].
	 *
	 * Odpowiedź jest identyczna dla adresu wolnego i zajętego (backend nie zdradza,
	 * kto ma konto), więc UI zawsze pokazuje „sprawdź skrzynkę", nigdy „adres
	 * zajęty". Ponowne wywołanie po minucie wysyła nowy link (resend-interval).
	 */
	suspend fun register(email: String, password: String) =
		http.post("$base/api/v1/auth/register") {
			contentType(ContentType.Application.Json)
			setBody(RegisterRequest(email, password))
		}.ensureSuccessOrThrow()

	/**
	 * Potwierdza adres tokenem z maila i dopiero wtedy zakłada konto — serwer od
	 * razu wydaje parę tokenów, więc po sukcesie użytkownik jest zalogowany.
	 * Token nieznany, wygasły i zużyty dają ten sam błąd 400
	 * `INVALID_VERIFICATION_TOKEN`.
	 */
	suspend fun verifyRegistration(token: String): AuthTokens =
		http.post("$base/api/v1/auth/register/verify") {
			contentType(ContentType.Application.Json)
			setBody(VerifyEmailRequest(token))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	suspend fun login(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(LoginRequest(email, password))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	/**
	 * Kończy sesję: unieważnia refresh token na serwerze (best-effort) i czyści
	 * lokalny stan. Nawet gdy żądanie do serwera padnie, lokalnie wylogowujemy.
	 */
	suspend fun logout() {
		val refreshToken = storage.getTokens()?.refreshToken
		if (refreshToken != null) {
			runCatching {
				http.post("$base/api/v1/auth/logout") {
					contentType(ContentType.Application.Json)
					setBody(RefreshRequest(refreshToken))
				}
			}
		}
		endSession()
	}

	/**
	 * Trwale usuwa konto zalogowanego użytkownika (hard delete, RODO — Claude.md §9).
	 * Wymaga ponownego podania hasła. Po sukcesie czyści lokalną sesję, więc UI wraca
	 * do ekranu logowania. Błędne hasło zwraca [SonglyApiException] (serwer: 4xx).
	 */
	suspend fun deleteAccount(password: String) {
		http.delete("$base/api/v1/auth/me") {
			contentType(ContentType.Application.Json)
			setBody(DeleteAccountRequest(password))
		}.ensureSuccessOrThrow()
		endSession()
	}

	suspend fun feed(page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/feed") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Tworzy nowy post autorstwa zalogowanego użytkownika. */
	suspend fun createPost(content: String): Post =
		http.post("$base/api/v1/posts") {
			contentType(ContentType.Application.Json)
			setBody(CreatePostRequest(content))
		}.decodeOrThrow()

	/**
	 * Pojedynczy post — np. z powiadomienia o lajku. Post niewidoczny (usunięty,
	 * autor zablokowany albo zawieszony) to 404 `POST_NOT_FOUND`: backend celowo nie
	 * odróżnia „nie ma" od „nie wolno Ci widzieć".
	 */
	suspend fun post(postId: String): Post =
		http.get("$base/api/v1/posts/$postId").decodeOrThrow()

	/**
	 * Posty jednego autora, najnowsze pierwsze. Bez postów publikowanych w
	 * społecznościach — te nie trafiają na profil autora (backend §8).
	 */
	suspend fun postsByAuthor(authorId: String, page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/posts") {
			parameter("authorId", authorId)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Usuwa własny post (backend: tylko autor; obcym zwraca błąd). */
	suspend fun deletePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId").ensureSuccessOrThrow()

	/** Polubienie posta (idempotentne po stronie serwera). */
	suspend fun likePost(postId: String) =
		http.put("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	/** Cofnięcie polubienia. */
	suspend fun unlikePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	/** Udostępnienie posta (idempotentne po stronie serwera). */
	suspend fun sharePost(postId: String) =
		http.put("$base/api/v1/posts/$postId/shares").ensureSuccessOrThrow()

	/** Cofnięcie udostępnienia. */
	suspend fun unsharePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId/shares").ensureSuccessOrThrow()

	/** Komentarze posta, stronicowane. */
	suspend fun comments(postId: String, page: Int = 0, size: Int = 50): Page<Comment> =
		http.get("$base/api/v1/posts/$postId/comments") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Dodaje komentarz do posta i zwraca go. */
	suspend fun addComment(postId: String, content: String): Comment =
		http.post("$base/api/v1/posts/$postId/comments") {
			contentType(ContentType.Application.Json)
			setBody(CreateCommentRequest(content))
		}.decodeOrThrow()

	/** Usuwa własny komentarz (backend: tylko autor; obcym zwraca błąd). */
	suspend fun deleteComment(postId: String, commentId: String) =
		http.delete("$base/api/v1/posts/$postId/comments/$commentId").ensureSuccessOrThrow()

	// --- Profil i ulubione gatunki (moduł profile) ---

	/** Profil zalogowanego użytkownika. */
	suspend fun myProfile(): Profile =
		http.get("$base/api/v1/profiles/me").decodeOrThrow()

	/** Publiczny profil innego użytkownika (dane profilowe + ulubione gatunki). */
	suspend fun profile(userId: String): PublicProfile =
		http.get("$base/api/v1/profiles/$userId").decodeOrThrow()

	/** Aktualizuje profil i zwraca zaktualizowaną wersję. */
	suspend fun updateProfile(displayName: String, bio: String?): Profile =
		http.put("$base/api/v1/profiles/me") {
			contentType(ContentType.Application.Json)
			setBody(UpdateProfileRequest(displayName, bio))
		}.decodeOrThrow()

	/** Ulubione gatunki zalogowanego użytkownika. */
	suspend fun myFavoriteGenres(): List<Genre> =
		http.get("$base/api/v1/profiles/me/genres").decodeOrThrow()

	/** Ustawia ulubione gatunki (backend wymaga zbioru niepustego). */
	suspend fun setFavoriteGenres(genreIds: Set<String>) =
		http.put("$base/api/v1/profiles/me/genres") {
			contentType(ContentType.Application.Json)
			setBody(SetFavoriteGenresRequest(genreIds))
		}.ensureSuccessOrThrow()

	/** Wszystkie dostępne gatunki, stronicowane. */
	suspend fun genres(page: Int = 0, size: Int = 100): Page<Genre> =
		http.get("$base/api/v1/genres") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	// --- Znajomi i zaproszenia (moduł social-graph) ---

	/** Wysyła zaproszenie do znajomych do użytkownika o danym id. */
	suspend fun sendFriendRequest(addresseeId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests") {
			contentType(ContentType.Application.Json)
			setBody(SendFriendRequestRequest(addresseeId))
		}.decodeOrThrow()

	/** Zaproszenia otrzymane (INCOMING) lub wysłane (OUTGOING). */
	suspend fun friendRequests(direction: String, page: Int = 0, size: Int = 20): Page<FriendRequest> =
		http.get("$base/api/v1/friends/requests") {
			parameter("direction", direction)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Akceptuje otrzymane zaproszenie. */
	suspend fun acceptFriendRequest(requestId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests/$requestId/accept").decodeOrThrow()

	/** Odrzuca otrzymane zaproszenie. */
	suspend fun declineFriendRequest(requestId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests/$requestId/decline").decodeOrThrow()

	/** Anuluje wysłane przez siebie zaproszenie. */
	suspend fun cancelFriendRequest(requestId: String) =
		http.delete("$base/api/v1/friends/requests/$requestId").ensureSuccessOrThrow()

	/** Lista znajomych, stronicowana. */
	suspend fun friends(page: Int = 0, size: Int = 20): Page<Friend> =
		http.get("$base/api/v1/friends") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Usuwa znajomego. */
	suspend fun removeFriend(userId: String) =
		http.delete("$base/api/v1/friends/$userId").ensureSuccessOrThrow()

	/** Wyszukuje użytkowników po fragmencie nazwy (do dodawania znajomych). */
	suspend fun searchUsers(query: String, page: Int = 0, size: Int = 20): Page<UserSearchResult> =
		http.get("$base/api/v1/profiles/search") {
			parameter("query", query)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/**
	 * Blokuje użytkownika (idempotentne). Po stronie serwera usuwa też znajomość
	 * i anuluje zaproszenia w obie strony — dlatego UI po blokadzie przeładowuje listy.
	 */
	suspend fun blockUser(userId: String) =
		http.put("$base/api/v1/blocks/$userId").ensureSuccessOrThrow()

	/** Odblokowuje użytkownika. */
	suspend fun unblockUser(userId: String) =
		http.delete("$base/api/v1/blocks/$userId").ensureSuccessOrThrow()

	/** Lista zablokowanych użytkowników, stronicowana. */
	suspend fun blockedUsers(page: Int = 0, size: Int = 20): Page<BlockedUser> =
		http.get("$base/api/v1/blocks") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	// --- Teraz słucham (moduł now-playing) ---

	/**
	 * Aktualny status „teraz słucham" zalogowanego użytkownika, albo `null` gdy nic
	 * nie gra (backend zwraca wtedy 204 bez ciała).
	 */
	suspend fun nowPlaying(): NowPlayingStatus? {
		val response = http.get("$base/api/v1/now-playing/me")
		return when {
			response.status.value == 204 -> null
			response.status.isSuccess() -> response.body()
			else -> throw response.toApiException()
		}
	}

	/**
	 * Rozpoczyna łączenie ze Spotify — zwraca URL autoryzacji do otwarcia w
	 * przeglądarce. Po zgodzie Spotify przekierowuje na publiczny callback backendu,
	 * który dokańcza połączenie; aplikacja po powrocie odświeża [nowPlaying].
	 */
	suspend fun connectSpotify(): String =
		http.post("$base/api/v1/now-playing/spotify/connect").decodeOrThrow<ConnectResponse>().authorizeUrl

	/** Odłącza konto Spotify. */
	suspend fun disconnectSpotify() =
		http.delete("$base/api/v1/now-playing/spotify").ensureSuccessOrThrow()

	/** Ustawia ręczny status „teraz słucham" (uniwersalny fallback dla dowolnego serwisu). */
	suspend fun setManualNowPlaying(title: String, artist: String?, album: String?, source: String?) =
		http.put("$base/api/v1/now-playing/manual") {
			contentType(ContentType.Application.Json)
			setBody(ManualNowPlayingRequest(title, artist, album, source))
		}.ensureSuccessOrThrow()

	/** Czyści ręczny status. */
	suspend fun clearManualNowPlaying() =
		http.delete("$base/api/v1/now-playing/manual").ensureSuccessOrThrow()

	/**
	 * Statusy znajomych „teraz". Backend zwraca tylko tych, którzy coś grają i nie
	 * wyłączyli widoczności — lista bywa krótsza niż lista znajomych.
	 */
	suspend fun friendsNowPlaying(): List<FriendNowPlaying> =
		http.get("$base/api/v1/now-playing/friends").decodeOrThrow()

	/** Czy własny status jest pokazywany znajomym. */
	suspend fun nowPlayingSettings(): NowPlayingSettings =
		http.get("$base/api/v1/now-playing/settings").decodeOrThrow()

	/** Włącza/wyłącza pokazywanie własnego statusu znajomym. */
	suspend fun updateNowPlayingSettings(shareWithFriends: Boolean) =
		http.put("$base/api/v1/now-playing/settings") {
			contentType(ContentType.Application.Json)
			setBody(UpdateNowPlayingSettingsRequest(shareWithFriends))
		}.ensureSuccessOrThrow()

	/**
	 * Statusy znajomych na żywo (ADR 0007 backendu): strumień SSE zamiast pollingu,
	 * bo odpytywanie o stan wszystkich znajomych co kilka sekund to ruch N×M, w
	 * większości bez żadnej zmiany.
	 *
	 * Każde podłączenie zaczyna się od pełnej migawki przez [friendsNowPlaying] —
	 * SSE niesie wyłącznie zmiany, więc bez tego po reconnekcie ekran zostałby ze
	 * stanem sprzed rozłączenia. Zerwane połączenie wraca z backoffem
	 * ([reconnectDelayMillis]); flow żyje aż do anulowania korutyny, więc UI zbiera
	 * go przez cały czas życia ekranu.
	 */
	fun friendsPresence(): Flow<FriendsPresence> = channelFlow {
		var friends = emptyList<FriendNowPlaying>()
		var attempt = 0
		var problem: PresenceProblem = PresenceProblem.CONNECTION
		while (isActive) {
			try {
				friends = friendsNowPlaying()
				send(FriendsPresence(friends, live = true))
				attempt = 0
				http.sse(
					"$base/api/v1/now-playing/stream",
					request = {
						// Domyślny read timeout silników (OkHttp: 10 s) jest krótszy niż
						// heartbeat backendu (20 s), więc bez tego zdrowy, tylko cichy
						// strumień zrywałby się w kółko. Zapas 3× heartbeat: dłuższa cisza
						// naprawdę znaczy, że połączenie umarło.
						timeout {
							socketTimeoutMillis = STREAM_SOCKET_TIMEOUT_MILLIS
							requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
						}
					},
				) {
					incoming.collect { event ->
						val data = event.data ?: return@collect
						friends = friends.applyPresenceChange(presenceJson.decodeFromString(data))
						send(FriendsPresence(friends, live = true))
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: SSEClientException) {
				// Backend odrzucił samo połączenie — status mówi, czy to limit strumieni
				// (wtedy nie ma sensu udawać, że to problem z siecią), czy coś innego.
				problem = presenceProblemFor(e.response?.status?.value)
			} catch (e: Exception) {
				// Zerwana sieć, restart backendu, padnięty parser — dla ekranu to jedno
				// i to samo: nie ma świeżych danych, spróbujemy za chwilę.
				problem = PresenceProblem.CONNECTION
			}
			send(FriendsPresence(friends, live = false, problem = problem))
			delay(reconnectDelayMillis(attempt))
			attempt++
		}
	}

	// --- Powiadomienia (moduł notification) ---

	/** Powiadomienia zalogowanego użytkownika, najnowsze pierwsze, stronicowane. */
	suspend fun notifications(page: Int = 0, size: Int = 20): Page<Notification> =
		http.get("$base/api/v1/notifications") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Liczba nieprzeczytanych powiadomień (do badge'a). */
	suspend fun unreadNotificationCount(): Long =
		http.get("$base/api/v1/notifications/unread-count").decodeOrThrow<UnreadCount>().count

	/** Oznacza pojedyncze powiadomienie jako przeczytane (idempotentne po stronie serwera). */
	suspend fun markNotificationRead(notificationId: String) =
		http.post("$base/api/v1/notifications/$notificationId/read").ensureSuccessOrThrow()

	/** Oznacza wszystkie powiadomienia jako przeczytane. */
	suspend fun markAllNotificationsRead() =
		http.post("$base/api/v1/notifications/read-all").ensureSuccessOrThrow()

	// --- Społeczności (moduł community) ---

	/** Wszystkie społeczności, stronicowane. [Community.myRole] mówi, czy jesteś członkiem. */
	suspend fun communities(page: Int = 0, size: Int = 20): Page<Community> =
		http.get("$base/api/v1/communities") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Społeczności, do których należy zalogowany użytkownik. */
	suspend fun myCommunities(page: Int = 0, size: Int = 20): Page<Community> =
		http.get("$base/api/v1/communities/mine") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/** Szczegóły jednej społeczności. */
	suspend fun community(communityId: String): Community =
		http.get("$base/api/v1/communities/$communityId").decodeOrThrow()

	/** Tworzy nową społeczność (zakładający zostaje jej właścicielem). */
	suspend fun createCommunity(name: String, description: String?): Community =
		http.post("$base/api/v1/communities") {
			contentType(ContentType.Application.Json)
			setBody(CreateCommunityRequest(name, description))
		}.decodeOrThrow()

	/** Edytuje społeczność (tylko właściciel — serwer waliduje). Zwraca zaktualizowaną wersję. */
	suspend fun updateCommunity(communityId: String, name: String, description: String?): Community =
		http.put("$base/api/v1/communities/$communityId") {
			contentType(ContentType.Application.Json)
			setBody(UpdateCommunityRequest(name, description))
		}.decodeOrThrow()

	/** Usuwa społeczność (tylko właściciel — serwer waliduje). */
	suspend fun deleteCommunity(communityId: String) =
		http.delete("$base/api/v1/communities/$communityId").ensureSuccessOrThrow()

	/** Dołącza do społeczności (idempotentne po stronie serwera). */
	suspend fun joinCommunity(communityId: String) =
		http.put("$base/api/v1/communities/$communityId/membership").ensureSuccessOrThrow()

	/** Opuszcza społeczność (właściciel nie może — serwer zwróci błąd). */
	suspend fun leaveCommunity(communityId: String) =
		http.delete("$base/api/v1/communities/$communityId/membership").ensureSuccessOrThrow()

	/** Członkowie społeczności, stronicowane. */
	suspend fun communityMembers(communityId: String, page: Int = 0, size: Int = 20): Page<CommunityMember> =
		http.get("$base/api/v1/communities/$communityId/members") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/**
	 * Mój status w społeczności: członek (z rolą), zbanowany (z powodem i
	 * terminem) albo nic. Powód bana dociera wyłącznie tym kanałem — nie ma go w
	 * mailu ani w błędzie `BANNED_FROM_COMMUNITY` (backend ADR 0015).
	 */
	suspend fun membershipStatus(communityId: String): MembershipStatus =
		http.get("$base/api/v1/communities/$communityId/membership").decodeOrThrow()

	/** Posty opublikowane w społeczności. Czytać może każdy zalogowany. */
	suspend fun communityPosts(communityId: String, page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/communities/$communityId/posts") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	/**
	 * Publikuje post w społeczności — tylko członek i tylko bez aktywnego bana
	 * (403 `NOT_COMMUNITY_MEMBER` / `BANNED_FROM_COMMUNITY`). Lajki, komentarze i
	 * usuwanie takiego posta idą zwykłymi endpointami postów.
	 */
	suspend fun createCommunityPost(communityId: String, content: String): Post =
		http.post("$base/api/v1/communities/$communityId/posts") {
			contentType(ContentType.Application.Json)
			setBody(CreatePostRequest(content))
		}.decodeOrThrow()

	// --- Dopasowywanie po gatunkach (moduł matching) ---

	/** Ranking użytkowników o podobnym guście (Jaccard na gatunkach), malejąco po score. */
	suspend fun matches(page: Int = 0, size: Int = 20): Page<Match> =
		http.get("$base/api/v1/matches") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	fun close() = http.close()

	private suspend fun startSession(tokens: AuthTokens) {
		storage.saveTokens(tokens)
		// Wymuś ponowne wczytanie tokenów przez wtyczkę Auth — inaczej trzymałaby
		// w cache poprzedni (albo brak) token z czasu sprzed logowania.
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend fun endSession() {
		storage.clear()
		cachedUserId = null
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
		if (status.isSuccess()) {
			return body()
		}
		throw toApiException()
	}

	/** Dla odpowiedzi bez ciała (204) — tylko weryfikuje status. */
	private suspend fun HttpResponse.ensureSuccessOrThrow() {
		if (!status.isSuccess()) {
			throw toApiException()
		}
	}

	private suspend fun HttpResponse.toApiException(): SonglyApiException {
		val error = runCatching { body<ApiError>() }.getOrNull()
		return SonglyApiException(
			status = status.value,
			error = error,
			message = error?.let { "${it.code}: ${it.message}" } ?: "HTTP ${status.value}",
		)
	}
}

// Trzykrotność heartbeatu presence z backendu (20 s) — patrz [SonglyApiClient.friendsPresence].
private const val STREAM_SOCKET_TIMEOUT_MILLIS = 60_000L
