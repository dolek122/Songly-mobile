package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import com.songly.shared.model.ApiError
import com.songly.shared.model.BlockStatus
import com.songly.shared.model.ProfileSummary
import com.songly.shared.model.AdminList
import com.songly.shared.model.AnalyticsSummary
import com.songly.shared.model.EscalatedAppeal
import com.songly.shared.model.SuspendAccountRequest
import com.songly.shared.model.SuspendedAccount
import com.songly.shared.model.Suspension
import com.songly.shared.model.SuspensionAppealRequest
import com.songly.shared.model.BanAppealRequest
import com.songly.shared.model.BanMemberRequest
import com.songly.shared.model.CommunityBan
import com.songly.shared.model.ModerationLogEntry
import com.songly.shared.model.PostReport
import com.songly.shared.model.ReportPostRequest
import com.songly.shared.model.SetMemberRoleRequest
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.Comment
import com.songly.shared.model.CompleteConnectRequest
import com.songly.shared.model.ConnectResponse
import com.songly.shared.model.CreateCommentRequest
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.CreateCommunityRequest
import com.songly.shared.model.ChangePasswordRequest
import com.songly.shared.model.CreatePostRequest
import com.songly.shared.model.PasswordResetConfirmRequest
import com.songly.shared.model.PasswordResetRequest
import com.songly.shared.model.PostImage
import com.songly.shared.model.UpdateCommunityRequest
import com.songly.shared.model.DeleteAccountRequest
import com.songly.shared.model.ManualNowPlayingRequest
import com.songly.shared.model.MailPreferences
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
import com.songly.shared.model.RegisterPushDeviceRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.RemovePushDeviceRequest
import com.songly.shared.model.SendFriendRequestRequest
import com.songly.shared.model.SetFavoriteGenresRequest
import com.songly.shared.model.SonglyApiException
import com.songly.shared.model.UnreadCount
import com.songly.shared.model.UpdateMailPreferencesRequest
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
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class SonglyApiClient internal constructor(
	config: ApiConfig,
	private val storage: TokenStorage,
	engine: HttpClientEngine?,
) {

	constructor(config: ApiConfig, storage: TokenStorage = InMemoryTokenStorage()) : this(config, storage, null)

	companion object {

		fun withEngine(config: ApiConfig, storage: TokenStorage, engine: HttpClientEngine): SonglyApiClient =
			SonglyApiClient(config, storage, engine)
	}

	private val http = createHttpClient(config, storage, engine)
	private val base = config.baseUrl.trimEnd('/')

	private val presenceJson = Json { ignoreUnknownKeys = true; isLenient = true }

	private var cachedUserId: String? = null

	private var registeredPushToken: String? = null

	@Throws(Exception::class)
	suspend fun isLoggedIn(): Boolean = storage.getTokens() != null

	@Throws(Exception::class)
	suspend fun currentUserId(): String =
		cachedUserId ?: myProfile().userId.also { cachedUserId = it }

	@Throws(Exception::class)
	suspend fun register(email: String, password: String) =
		http.post("$base/api/v1/auth/register") {
			contentType(ContentType.Application.Json)
			setBody(RegisterRequest(email, password))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun verifyRegistration(token: String): AuthTokens =
		http.post("$base/api/v1/auth/register/verify") {
			contentType(ContentType.Application.Json)
			setBody(VerifyEmailRequest(token))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	@Throws(Exception::class)
	suspend fun login(email: String, password: String): AuthTokens =
		http.post("$base/api/v1/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(LoginRequest(email, password))
		}.decodeOrThrow<AuthTokens>().also { startSession(it) }

	@Throws(Exception::class)
	suspend fun logout() {
		val refreshToken = storage.getTokens()?.refreshToken
		registeredPushToken?.let { token -> runCatching { unregisterPushDevice(token) } }
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

	@Throws(Exception::class)
	suspend fun deleteAccount(password: String) {
		http.delete("$base/api/v1/auth/me") {
			contentType(ContentType.Application.Json)
			setBody(DeleteAccountRequest(password))
		}.ensureSuccessOrThrow()
		endSession()
	}

	@Throws(Exception::class)
	suspend fun changePassword(currentPassword: String, newPassword: String) {
		http.put("$base/api/v1/auth/password") {
			contentType(ContentType.Application.Json)
			setBody(ChangePasswordRequest(currentPassword, newPassword))
		}.ensureSuccessOrThrow()
		endSession()
	}

	@Throws(Exception::class)
	suspend fun requestPasswordReset(email: String) =
		http.post("$base/api/v1/auth/password/reset-request") {
			contentType(ContentType.Application.Json)
			setBody(PasswordResetRequest(email))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun resetPassword(token: String, newPassword: String) =
		http.post("$base/api/v1/auth/password/reset") {
			contentType(ContentType.Application.Json)
			setBody(PasswordResetConfirmRequest(token, newPassword))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun uploadAvatar(image: ByteArray, contentType: String): Profile =
		http.post("$base/api/v1/profiles/me/avatar") {
			setBody(imageForm(image, contentType))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun deleteAvatar() =
		http.delete("$base/api/v1/profiles/me/avatar").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun uploadPostImage(image: ByteArray, contentType: String): PostImage =
		http.post("$base/api/v1/posts/image") {
			setBody(imageForm(image, contentType))
		}.decodeOrThrow()

	private fun imageForm(image: ByteArray, contentType: String) = MultiPartFormDataContent(
		formData {
			append(
				"file",
				image,
				Headers.build {
					append(HttpHeaders.ContentType, contentType)
					append(HttpHeaders.ContentDisposition, "filename=\"image\"")
				},
			)
		},
	)

	@Throws(Exception::class)
	suspend fun feed(page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/feed") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun createPost(content: String, imageKey: String? = null): Post =
		http.post("$base/api/v1/posts") {
			contentType(ContentType.Application.Json)
			setBody(CreatePostRequest(content, imageKey))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun post(postId: String): Post =
		http.get("$base/api/v1/posts/$postId").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun postsByAuthor(authorId: String, page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/posts") {
			parameter("authorId", authorId)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun deletePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun likePost(postId: String) =
		http.put("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun unlikePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId/likes").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun sharePost(postId: String) =
		http.put("$base/api/v1/posts/$postId/shares").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun unsharePost(postId: String) =
		http.delete("$base/api/v1/posts/$postId/shares").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun comments(postId: String, page: Int = 0, size: Int = 50): Page<Comment> =
		http.get("$base/api/v1/posts/$postId/comments") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun addComment(postId: String, content: String): Comment =
		http.post("$base/api/v1/posts/$postId/comments") {
			contentType(ContentType.Application.Json)
			setBody(CreateCommentRequest(content))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun deleteComment(postId: String, commentId: String) =
		http.delete("$base/api/v1/posts/$postId/comments/$commentId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun myProfile(): Profile =
		http.get("$base/api/v1/profiles/me").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun profile(userId: String): PublicProfile =
		http.get("$base/api/v1/profiles/$userId").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun updateProfile(displayName: String, bio: String?): Profile =
		http.put("$base/api/v1/profiles/me") {
			contentType(ContentType.Application.Json)
			setBody(UpdateProfileRequest(displayName, bio))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun myFavoriteGenres(): List<Genre> =
		http.get("$base/api/v1/profiles/me/genres").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun setFavoriteGenres(genreIds: Set<String>) =
		http.put("$base/api/v1/profiles/me/genres") {
			contentType(ContentType.Application.Json)
			setBody(SetFavoriteGenresRequest(genreIds))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun genres(page: Int = 0, size: Int = 100): Page<Genre> =
		http.get("$base/api/v1/genres") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun sendFriendRequest(addresseeId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests") {
			contentType(ContentType.Application.Json)
			setBody(SendFriendRequestRequest(addresseeId))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun friendRequests(direction: String, page: Int = 0, size: Int = 20): Page<FriendRequest> =
		http.get("$base/api/v1/friends/requests") {
			parameter("direction", direction)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun acceptFriendRequest(requestId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests/$requestId/accept").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun declineFriendRequest(requestId: String): FriendRequest =
		http.post("$base/api/v1/friends/requests/$requestId/decline").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun cancelFriendRequest(requestId: String) =
		http.delete("$base/api/v1/friends/requests/$requestId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun friends(page: Int = 0, size: Int = 20): Page<Friend> =
		http.get("$base/api/v1/friends") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun removeFriend(userId: String) =
		http.delete("$base/api/v1/friends/$userId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun searchUsers(query: String, page: Int = 0, size: Int = 20): Page<UserSearchResult> =
		http.get("$base/api/v1/profiles/search") {
			parameter("query", query)
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun blockUser(userId: String) =
		http.put("$base/api/v1/blocks/$userId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun unblockUser(userId: String) =
		http.delete("$base/api/v1/blocks/$userId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun isBlocking(userId: String): Boolean =
		http.get("$base/api/v1/blocks/$userId").decodeOrThrow<BlockStatus>().blocked

	@Throws(Exception::class)
	suspend fun blockedUsers(page: Int = 0, size: Int = 20): Page<BlockedUser> =
		http.get("$base/api/v1/blocks") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun nowPlaying(): NowPlayingStatus? {
		val response = http.get("$base/api/v1/now-playing/me")
		return when {
			response.status.value == 204 -> null
			response.status.isSuccess() -> response.body()
			else -> {
				val error = response.toApiException()
				if (error.error?.code == "PROVIDER_NOT_CONNECTED") null else throw error
			}
		}
	}

	@Throws(Exception::class)
	suspend fun connectSpotify(): String =
		http.post("$base/api/v1/now-playing/spotify/connect").decodeOrThrow<ConnectResponse>().authorizeUrl

	@Throws(Exception::class)
	suspend fun completeSpotifyConnect(ticket: String) =
		http.post("$base/api/v1/now-playing/spotify/connect/complete") {
			contentType(ContentType.Application.Json)
			setBody(CompleteConnectRequest(ticket))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun disconnectSpotify() =
		http.delete("$base/api/v1/now-playing/spotify").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun setManualNowPlaying(title: String, artist: String?, album: String?, source: String?) =
		http.put("$base/api/v1/now-playing/manual") {
			contentType(ContentType.Application.Json)
			setBody(ManualNowPlayingRequest(title, artist, album, source))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun clearManualNowPlaying() =
		http.delete("$base/api/v1/now-playing/manual").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun friendsNowPlaying(): List<FriendNowPlaying> =
		http.get("$base/api/v1/now-playing/friends").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun nowPlayingSettings(): NowPlayingSettings =
		http.get("$base/api/v1/now-playing/settings").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun updateNowPlayingSettings(shareWithFriends: Boolean) =
		http.put("$base/api/v1/now-playing/settings") {
			contentType(ContentType.Application.Json)
			setBody(UpdateNowPlayingSettingsRequest(shareWithFriends))
		}.ensureSuccessOrThrow()

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
				problem = presenceProblemFor(e.response?.status?.value)
			} catch (e: Exception) {
				problem = PresenceProblem.CONNECTION
			}
			send(FriendsPresence(friends, live = false, problem = problem))
			delay(reconnectDelayMillis(attempt))
			attempt++
		}
	}

	@Throws(Exception::class)
	suspend fun notifications(page: Int = 0, size: Int = 20): Page<Notification> =
		http.get("$base/api/v1/notifications") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun unreadNotificationCount(): Long =
		http.get("$base/api/v1/notifications/unread-count").decodeOrThrow<UnreadCount>().count

	@Throws(Exception::class)
	suspend fun markNotificationRead(notificationId: String) =
		http.post("$base/api/v1/notifications/$notificationId/read").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun markAllNotificationsRead() =
		http.post("$base/api/v1/notifications/read-all").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun mailPreferences(): MailPreferences =
		http.get("$base/api/v1/notifications/mail-preferences").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun registerPushDevice(token: String, platform: String) {
		http.put("$base/api/v1/notifications/push-devices") {
			contentType(ContentType.Application.Json)
			setBody(RegisterPushDeviceRequest(token, platform))
		}.ensureSuccessOrThrow()
		registeredPushToken = token
	}

	@Throws(Exception::class)
	suspend fun unregisterPushDevice(token: String) {
		http.post("$base/api/v1/notifications/push-devices/removal") {
			contentType(ContentType.Application.Json)
			setBody(RemovePushDeviceRequest(token))
		}.ensureSuccessOrThrow()
		if (registeredPushToken == token) registeredPushToken = null
	}

	@Throws(Exception::class)
	suspend fun updateMailPreferences(preferences: MailPreferences) =
		http.put("$base/api/v1/notifications/mail-preferences") {
			contentType(ContentType.Application.Json)
			setBody(UpdateMailPreferencesRequest(preferences.decisionsAboutMe, preferences.moderationQueue))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun communities(page: Int = 0, size: Int = 20): Page<Community> =
		http.get("$base/api/v1/communities") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun myCommunities(page: Int = 0, size: Int = 20): Page<Community> =
		http.get("$base/api/v1/communities/mine") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun community(communityId: String): Community =
		http.get("$base/api/v1/communities/$communityId").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun createCommunity(name: String, description: String?): Community =
		http.post("$base/api/v1/communities") {
			contentType(ContentType.Application.Json)
			setBody(CreateCommunityRequest(name, description))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun updateCommunity(communityId: String, name: String, description: String?): Community =
		http.put("$base/api/v1/communities/$communityId") {
			contentType(ContentType.Application.Json)
			setBody(UpdateCommunityRequest(name, description))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun deleteCommunity(communityId: String) =
		http.delete("$base/api/v1/communities/$communityId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun joinCommunity(communityId: String) =
		http.put("$base/api/v1/communities/$communityId/membership").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun leaveCommunity(communityId: String) =
		http.delete("$base/api/v1/communities/$communityId/membership").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun communityMembers(communityId: String, page: Int = 0, size: Int = 20): Page<CommunityMember> =
		http.get("$base/api/v1/communities/$communityId/members") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun membershipStatus(communityId: String): MembershipStatus =
		http.get("$base/api/v1/communities/$communityId/membership").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun communityPosts(communityId: String, page: Int = 0, size: Int = 20): Page<Post> =
		http.get("$base/api/v1/communities/$communityId/posts") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun createCommunityPost(communityId: String, content: String, imageKey: String? = null): Post =
		http.post("$base/api/v1/communities/$communityId/posts") {
			contentType(ContentType.Application.Json)
			setBody(CreatePostRequest(content, imageKey))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun reportPost(postId: String, reason: String, details: String?) =
		http.post("$base/api/v1/posts/$postId/reports") {
			contentType(ContentType.Application.Json)
			setBody(ReportPostRequest(reason, details))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun postReports(communityId: String, page: Int = 0, size: Int = 20): Page<PostReport> =
		http.get("$base/api/v1/communities/$communityId/post-reports") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun dismissReport(communityId: String, reportId: String) =
		http.post("$base/api/v1/communities/$communityId/post-reports/$reportId/dismissal").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun communityBans(communityId: String, page: Int = 0, size: Int = 20): Page<CommunityBan> =
		http.get("$base/api/v1/communities/$communityId/bans") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun banMember(communityId: String, userId: String, reason: String?, durationDays: Int?) =
		http.put("$base/api/v1/communities/$communityId/bans/$userId") {
			contentType(ContentType.Application.Json)
			setBody(BanMemberRequest(reason, durationDays))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun unbanMember(communityId: String, userId: String) =
		http.delete("$base/api/v1/communities/$communityId/bans/$userId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun rejectBanAppeal(communityId: String, userId: String) =
		http.post("$base/api/v1/communities/$communityId/bans/$userId/appeal/rejection").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun setMemberRole(communityId: String, userId: String, role: String) =
		http.put("$base/api/v1/communities/$communityId/members/$userId/role") {
			contentType(ContentType.Application.Json)
			setBody(SetMemberRoleRequest(role))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun communityModerationLog(communityId: String, page: Int = 0, size: Int = 20): Page<ModerationLogEntry> =
		http.get("$base/api/v1/communities/$communityId/moderation-log") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun appealBan(communityId: String, text: String) =
		http.post("$base/api/v1/communities/$communityId/ban-appeal") {
			contentType(ContentType.Application.Json)
			setBody(BanAppealRequest(text))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun escalateBanAppeal(communityId: String) =
		http.post("$base/api/v1/communities/$communityId/ban-appeal/escalation").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun mySuspension(): Suspension =
		http.get("$base/api/v1/auth/me/suspension").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun appealSuspension(text: String) =
		http.post("$base/api/v1/auth/me/suspension/appeal") {
			contentType(ContentType.Application.Json)
			setBody(SuspensionAppealRequest(text))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun admins(): AdminList =
		http.get("$base/api/v1/admin/admins").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun isCurrentUserAdmin(): Boolean = try {
		currentUserId() in admins().userIds
	} catch (e: SonglyApiException) {
		if (e.status == 403) false else throw e
	}

	@Throws(Exception::class)
	suspend fun grantAdmin(userId: String) =
		http.put("$base/api/v1/admin/users/$userId/admin").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun revokeAdmin(userId: String) =
		http.delete("$base/api/v1/admin/users/$userId/admin").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun suspensions(): List<SuspendedAccount> =
		http.get("$base/api/v1/admin/suspensions").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun suspendUser(userId: String, reason: String, durationDays: Int?) =
		http.put("$base/api/v1/admin/users/$userId/suspension") {
			contentType(ContentType.Application.Json)
			setBody(SuspendAccountRequest(reason, durationDays))
		}.ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun liftSuspension(userId: String) =
		http.delete("$base/api/v1/admin/users/$userId/suspension").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun rejectSuspensionAppeal(userId: String) =
		http.post("$base/api/v1/admin/users/$userId/suspension/appeal/rejection").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun escalatedBanAppeals(): List<EscalatedAppeal> =
		http.get("$base/api/v1/admin/ban-appeals").decodeOrThrow()

	@Throws(Exception::class)
	suspend fun overturnBan(communityId: String, userId: String) =
		http.delete("$base/api/v1/admin/communities/$communityId/bans/$userId").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun upholdBan(communityId: String, userId: String) =
		http.post("$base/api/v1/admin/communities/$communityId/bans/$userId/escalation-rejection").ensureSuccessOrThrow()

	@Throws(Exception::class)
	suspend fun platformModerationLog(userId: String? = null, page: Int = 0, size: Int = 20): Page<ModerationLogEntry> =
		http.get("$base/api/v1/admin/moderation-log") {
			userId?.let { parameter("userId", it) }
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun adminProfileSummaries(userIds: Collection<String>): List<ProfileSummary> =
		http.get("$base/api/v1/admin/profiles") {
			parameter("userIds", userIds.joinToString(","))
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun analyticsSummary(days: Int): AnalyticsSummary =
		http.get("$base/api/v1/analytics/summary") {
			parameter("days", days)
		}.decodeOrThrow()

	@Throws(Exception::class)
	suspend fun matches(page: Int = 0, size: Int = 20): Page<Match> =
		http.get("$base/api/v1/matches") {
			parameter("page", page)
			parameter("size", size)
		}.decodeOrThrow()

	fun close() = http.close()

	private suspend fun startSession(tokens: AuthTokens) {
		storage.saveTokens(tokens)
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend fun endSession() {
		storage.clear()
		cachedUserId = null
		registeredPushToken = null
		http.authProvider<BearerAuthProvider>()?.clearToken()
	}

	private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
		if (status.isSuccess()) {
			return body()
		}
		throw toApiException()
	}

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

private const val STREAM_SOCKET_TIMEOUT_MILLIS = 60_000L
