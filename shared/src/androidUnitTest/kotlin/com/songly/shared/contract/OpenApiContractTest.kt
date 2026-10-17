package com.songly.shared.contract

import com.songly.shared.model.ApiError
import com.songly.shared.model.AdminList
import com.songly.shared.model.AnalyticsCount
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
import com.songly.shared.model.ChangePasswordRequest
import com.songly.shared.model.PasswordResetConfirmRequest
import com.songly.shared.model.PasswordResetRequest
import com.songly.shared.model.PostImage
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Comment
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.ConnectResponse
import com.songly.shared.model.CreateCommentRequest
import com.songly.shared.model.CreateCommunityRequest
import com.songly.shared.model.CreatePostRequest
import com.songly.shared.model.DeleteAccountRequest
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.model.FriendRequest
import com.songly.shared.model.Genre
import com.songly.shared.model.LoginRequest
import com.songly.shared.model.ManualNowPlayingRequest
import com.songly.shared.model.Match
import com.songly.shared.model.MembershipStatus
import com.songly.shared.model.BanDetails
import com.songly.shared.model.Notification
import com.songly.shared.model.NowPlayingSettings
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.model.PageMetadata
import com.songly.shared.model.Post
import com.songly.shared.model.Profile
import com.songly.shared.model.PublicProfile
import com.songly.shared.model.RefreshRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.SendFriendRequestRequest
import com.songly.shared.model.SetFavoriteGenresRequest
import com.songly.shared.model.UnreadCount
import com.songly.shared.model.UpdateCommunityRequest
import com.songly.shared.model.UpdateNowPlayingSettingsRequest
import com.songly.shared.model.UpdateProfileRequest
import com.songly.shared.model.UserSearchResult
import com.songly.shared.model.VerifyEmailRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Wykrywa dryf między ręcznie pisanymi modelami mobilnymi a kontraktem OpenAPI
 * backendu ({@code shared/contract/openapi.json}, zsynchronizowana kopia
 * {@code docs/api/openapi.json} z Songly-backend).
 *
 * Reguła: **każde pole modelu musi istnieć w schemacie backendu**. Modelom wolno
 * być podzbiorem (świadomie pomijamy pola nieużywane w UI — Ktor je ignoruje),
 * ale nie wolno im czytać pola, którego backend już nie ma. To łapie właśnie
 * scenariusz „backend zmienił/usunął pole, a mobilka o tym nie wie" — inaczej
 * `ignoreUnknownKeys` po cichu podałby wartość domyślną zamiast danych.
 *
 * Gdy test padnie po odświeżeniu kontraktu: dostosuj wskazany model do nowego
 * kształtu DTO. Kontrakt odświeżasz kopiując plik z backendu (patrz README).
 */
class OpenApiContractTest {

	/** Model mobilny -> nazwa schematu w `components/schemas` backendu. */
	private val mapping: List<Pair<KSerializer<*>, String>> = listOf(
		RegisterRequest.serializer() to "RegisterRequest",
		VerifyEmailRequest.serializer() to "VerifyEmailRequest",
		LoginRequest.serializer() to "LoginRequest",
		RefreshRequest.serializer() to "RefreshRequest",
		DeleteAccountRequest.serializer() to "DeleteAccountRequest",
		ChangePasswordRequest.serializer() to "ChangePasswordRequest",
		PasswordResetRequest.serializer() to "PasswordResetRequest",
		PasswordResetConfirmRequest.serializer() to "PasswordResetConfirmRequest",
		PostImage.serializer() to "PostImageResponse",
		AuthTokens.serializer() to "AuthTokensResponse",
		Post.serializer() to "PostResponse",
		CreatePostRequest.serializer() to "CreatePostRequest",
		PageMetadata.serializer() to "PageMetadata",
		Comment.serializer() to "CommentResponse",
		CreateCommentRequest.serializer() to "CreateCommentRequest",
		Friend.serializer() to "FriendResponse",
		FriendRequest.serializer() to "FriendRequestResponse",
		SendFriendRequestRequest.serializer() to "SendFriendRequestRequest",
		BlockedUser.serializer() to "BlockedUserResponse",
		UserSearchResult.serializer() to "UserSearchResponse",
		Notification.serializer() to "NotificationResponse",
		UnreadCount.serializer() to "UnreadCountResponse",
		Match.serializer() to "MatchResponse",
		Profile.serializer() to "ProfileResponse",
		Genre.serializer() to "GenreResponse",
		PublicProfile.serializer() to "PublicProfileResponse",
		UpdateProfileRequest.serializer() to "UpdateProfileRequest",
		SetFavoriteGenresRequest.serializer() to "SetFavoriteGenresRequest",
		Community.serializer() to "CommunityResponse",
		CommunityMember.serializer() to "CommunityMemberResponse",
		MembershipStatus.serializer() to "MembershipStatusResponse",
		BanDetails.serializer() to "BanDetailsResponse",
		CreateCommunityRequest.serializer() to "CreateCommunityRequest",
		UpdateCommunityRequest.serializer() to "UpdateCommunityRequest",
		NowPlayingStatus.serializer() to "NowPlayingStatusResponse",
		FriendNowPlaying.serializer() to "FriendNowPlayingResponse",
		NowPlayingSettings.serializer() to "NowPlayingSettingsResponse",
		UpdateNowPlayingSettingsRequest.serializer() to "UpdateNowPlayingSettingsRequest",
		ManualNowPlayingRequest.serializer() to "ManualNowPlayingRequest",
		ConnectResponse.serializer() to "ConnectResponse",
		ReportPostRequest.serializer() to "ReportPostRequest",
		PostReport.serializer() to "PostReportResponse",
		CommunityBan.serializer() to "CommunityBanResponse",
		BanMemberRequest.serializer() to "BanMemberRequest",
		SetMemberRoleRequest.serializer() to "SetMemberRoleRequest",
		BanAppealRequest.serializer() to "BanAppealRequest",
		ModerationLogEntry.serializer() to "ModerationLogEntryResponse",
		AdminList.serializer() to "AdminListResponse",
		SuspendAccountRequest.serializer() to "SuspendAccountRequest",
		SuspendedAccount.serializer() to "SuspendedAccountResponse",
		EscalatedAppeal.serializer() to "EscalatedAppealResponse",
		AnalyticsSummary.serializer() to "AnalyticsSummaryResponse",
		AnalyticsCount.serializer() to "AnalyticsCountResponse",
		Suspension.serializer() to "SuspensionResponse",
		SuspensionAppealRequest.serializer() to "SuspensionAppealRequest",
		ApiError.serializer() to "ApiError",
	)

	/**
	 * Pola backendu, których model świadomie nie czyta (schemat -> pola). Pusta
	 * mapa znaczy „bierzemy wszystko" — każdy wpis tutaj to decyzja do
	 * uzasadnienia w komentarzu, nie miejsce na chowanie zaległości.
	 */
	private val intentionallyIgnored: Map<String, Set<String>> = emptyMap()

	@Test
	fun everyModelFieldExistsInBackendSchema() {
		val schemas = loadSchemas()
		val violations = mutableListOf<String>()

		for ((serializer, schemaName) in mapping) {
			val schema = schemas[schemaName]?.jsonObject
			if (schema == null) {
				violations += "Brak schematu '$schemaName' w kontrakcie (model ${serializer.descriptor.serialName}) " +
					"— literówka w mapowaniu albo DTO usunięte z backendu."
				continue
			}
			val backendFields = schema["properties"]?.jsonObject?.keys.orEmpty()
			val descriptor = serializer.descriptor
			val modelFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
			val missing = modelFields.filterNot { it in backendFields }
			if (missing.isNotEmpty()) {
				violations += "Model ${descriptor.serialName} (schemat $schemaName): pola $missing " +
					"nie istnieją w backendzie. Dostępne: ${backendFields.sorted()}"
			}
		}

		if (violations.isNotEmpty()) {
			fail("Dryf modeli mobilnych względem kontraktu OpenAPI:\n" + violations.joinToString("\n") { "  - $it" })
		}
	}

	/**
	 * Drugi kierunek: **pole dodane w backendzie musi trafić do modelu** albo na
	 * listę świadomie pomijanych ([intentionallyIgnored]).
	 *
	 * Sam test wyżej łapie dryf tylko wtedy, gdy backend coś *usunie*. Częstszy
	 * przypadek jest odwrotny: serwer zaczyna zwracać nowe pole, mobilka o nim nie
	 * wie i po cichu go nie pokazuje. `ignoreUnknownKeys` sprawia, że nic nigdzie
	 * nie wybucha, więc taki brak potrafi przeżyć miesiące.
	 */
	@Test
	fun everyBackendFieldIsReadOrExplicitlyIgnored() {
		val schemas = loadSchemas()
		val violations = mutableListOf<String>()

		for ((serializer, schemaName) in mapping) {
			val backendFields = schemas[schemaName]?.jsonObject?.get("properties")?.jsonObject?.keys.orEmpty()
			val descriptor = serializer.descriptor
			val modelFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
			val unread = backendFields - modelFields - intentionallyIgnored[schemaName].orEmpty()
			if (unread.isNotEmpty()) {
				violations += "Model ${descriptor.serialName} (schemat $schemaName): backend zwraca pola " +
					"${unread.sorted()}, których model nie czyta. Dodaj je do modelu albo — jeśli celowo " +
					"ich nie potrzebujemy — do intentionallyIgnored w tym teście."
			}
		}

		if (violations.isNotEmpty()) {
			fail("Mobilka nie nadąża za kontraktem OpenAPI:\n" + violations.joinToString("\n") { "  - $it" })
		}
	}

	private fun loadSchemas() = run {
		val path = System.getProperty("songly.openapi.contract")
			?: fail("Brak property 'songly.openapi.contract' — sprawdź testOptions w shared/build.gradle.kts")
		val file = File(path)
		if (!file.exists()) fail("Nie znaleziono kontraktu OpenAPI: $path")
		val root = Json.parseToJsonElement(file.readText()).jsonObject
		root["components"]?.jsonObject?.get("schemas")?.jsonObject
			?: fail("Kontrakt nie zawiera components/schemas")
	}
}
